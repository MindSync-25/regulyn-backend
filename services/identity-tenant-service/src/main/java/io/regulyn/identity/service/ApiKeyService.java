package io.regulyn.identity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.model.ActorType;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import io.regulyn.identity.client.EvidenceClient;
import io.regulyn.identity.constants.TenantStatuses;
import io.regulyn.identity.dto.ApiKeyCreateRequest;
import io.regulyn.identity.dto.ApiKeyCreateResponse;
import io.regulyn.identity.dto.ApiKeyRevokeRequest;
import io.regulyn.identity.dto.ApiKeyRevokeResponse;
import io.regulyn.identity.dto.ApiKeyRotateResponse;
import io.regulyn.identity.entity.ApiKey;
import io.regulyn.identity.entity.IdempotencyKeyEntity;
import io.regulyn.identity.entity.IdempotencyKeyId;
import io.regulyn.identity.entity.Tenant;
import io.regulyn.identity.repository.ApiKeyRepository;
import io.regulyn.identity.repository.IdempotencyKeyRepository;
import io.regulyn.identity.repository.TenantRepository;
import io.regulyn.identity.util.CanonicalJson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApiKeyService {

    private static final String SCOPE_CREATE_API_KEY = "POST:/api-keys";
    private static final int IDEMPOTENCY_TTL_DAYS = 7;

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ApiKeyTokenService apiKeyTokenService;
    private final InviteTokenCrypto inviteTokenCrypto;
    private final EvidenceClient evidenceClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final String serviceName;
    private final TenantWriteGuard tenantWriteGuard;

    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         TenantRepository tenantRepository,
                         IdempotencyKeyRepository idempotencyKeyRepository,
                         ApiKeyTokenService apiKeyTokenService,
                         InviteTokenCrypto inviteTokenCrypto,
                         EvidenceClient evidenceClient,
                         AuditWriter auditWriter,
                         OutboxWriter outboxWriter,
                         ObjectMapper objectMapper,
                         TenantWriteGuard tenantWriteGuard,
                         PlatformTransactionManager transactionManager,
                         @Value("${spring.application.name:identity-tenant-service}") String serviceName) {
        this.apiKeyRepository = apiKeyRepository;
        this.tenantRepository = tenantRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.apiKeyTokenService = apiKeyTokenService;
        this.inviteTokenCrypto = inviteTokenCrypto;
        this.evidenceClient = evidenceClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.tenantWriteGuard = tenantWriteGuard;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.serviceName = serviceName;
    }

    public ApiKeyCreateResponse createApiKey(ApiKeyCreateRequest request, String idempotencyKey) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_CONTEXT_REQUIRED");
        }
        tenantWriteGuard.guardWrite(tenantId, "API_KEY_CREATE");
        ensureTenantActive(tenantId);

        String keyName = normalizeKeyName(request != null ? request.getName() : null);
        Instant expiresAt = resolveExpiresAt(request != null ? request.getExpiresAt() : null,
                request != null ? request.getExpiresInDays() : null);

        String requestHash = computeRequestHash(tenantId, keyName, expiresAt);

        return transactionTemplate.execute(status -> {
            IdempotencyKeyEntity existingKey = null;
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                existingKey = idempotencyKeyRepository.findForUpdate(tenantId, SCOPE_CREATE_API_KEY, idempotencyKey)
                        .orElse(null);
                if (existingKey != null && isIdempotencyExpired(existingKey)) {
                    idempotencyKeyRepository.delete(existingKey);
                    existingKey = null;
                }
                if (existingKey != null) {
                    if (!requestHash.equals(existingKey.getRequestHash())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED_DIFFERENT_REQUEST");
                    }
                    return responseFromIdempotency(existingKey);
                }
            }

                Map<String, Object> evidenceMetadata = new LinkedHashMap<>();
                evidenceMetadata.put("tenantId", tenantId.toString());
                evidenceMetadata.put("keyName", keyName);
                evidenceMetadata.put("expiresAt", expiresAt != null ? expiresAt.toString() : null);

                UUID evidenceId = createEvidence(
                    tenantId,
                    "API_KEY_CREATED",
                    "API_KEY_CREATED",
                    "API key created",
                    evidenceMetadata
                );

            String rawKey = apiKeyTokenService.generateRawKey();
            String keyHash = apiKeyTokenService.hashHmac(rawKey);
            String prefix = buildPrefix(rawKey);

            ApiKey apiKey = new ApiKey();
            apiKey.setTenantId(tenantId);
            apiKey.setKeyName(keyName);
            apiKey.setApiKeyHash(keyHash);
            apiKey.setEnabled(true);
            apiKey.setExpiresAt(expiresAt);
            apiKey.setKeyVersion(1);
            apiKey.setPrefix(prefix);
            apiKey.setHashAlg("HMAC_SHA256");
            apiKey.setCreatedBy(TenantContextHolder.getUserId());

            apiKeyRepository.save(apiKey);

            ApiKeyCreateResponse response = buildCreateResponse(apiKey, rawKey);
            writeAuditOutboxCreate(apiKey, response, evidenceId);

            if (existingKey == null && idempotencyKey != null && !idempotencyKey.isBlank()) {
                String aad = buildIdempotencyAad(tenantId, SCOPE_CREATE_API_KEY, idempotencyKey);
                InviteTokenCrypto.EncryptedToken encrypted = inviteTokenCrypto.encrypt(rawKey, aad);
                String responseJson = serializeCreateResponse(response, encrypted);

                IdempotencyKeyEntity entity = new IdempotencyKeyEntity();
                entity.setId(new IdempotencyKeyId(tenantId, SCOPE_CREATE_API_KEY, idempotencyKey));
                entity.setRequestHash(requestHash);
                entity.setResponseJson(responseJson);
                entity.setExpiresAt(resolveIdempotencyExpiry(expiresAt));
                idempotencyKeyRepository.save(entity);
            }

            return response;
        });
    }

    public ApiKeyRotateResponse rotateApiKey(UUID apiKeyId, ApiKeyCreateRequest request, String idempotencyKey) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_CONTEXT_REQUIRED");
        }
        tenantWriteGuard.guardWrite(tenantId, "API_KEY_ROTATE");
        ensureTenantActive(tenantId);

        Instant expiresAt = resolveExpiresAt(request != null ? request.getExpiresAt() : null,
                request != null ? request.getExpiresInDays() : null);
        String scope = "POST:/api-keys/" + apiKeyId + "/rotate";
        String requestHash = computeRotateRequestHash(tenantId, apiKeyId, expiresAt);

        return transactionTemplate.execute(status -> {
            IdempotencyKeyEntity existingKey = null;
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                existingKey = idempotencyKeyRepository.findForUpdate(tenantId, scope, idempotencyKey)
                        .orElse(null);
                if (existingKey != null && isIdempotencyExpired(existingKey)) {
                    idempotencyKeyRepository.delete(existingKey);
                    existingKey = null;
                }
                if (existingKey != null) {
                    if (!requestHash.equals(existingKey.getRequestHash())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED_DIFFERENT_REQUEST");
                    }
                    return responseFromRotateIdempotency(existingKey);
                }
            }

            ApiKey existing = apiKeyRepository.findByIdForUpdate(apiKeyId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API_KEY_NOT_FOUND"));

            if (!tenantId.equals(existing.getTenantId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CROSS_TENANT_FORBIDDEN");
            }
            if (!Boolean.TRUE.equals(existing.getEnabled()) || existing.getRevokedAt() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "API_KEY_REVOKED");
            }
            if (existing.getExpiresAt() != null && existing.getExpiresAt().isBefore(Instant.now())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "API_KEY_EXPIRED");
            }

            Instant effectiveExpiresAt = expiresAt != null ? expiresAt : existing.getExpiresAt();

            UUID evidenceId = createEvidence(
                    tenantId,
                    "API_KEY_ROTATED",
                    "API_KEY_ROTATED",
                    "API key rotated",
                    Map.of(
                            "tenantId", tenantId.toString(),
                            "oldApiKeyId", existing.getApiKeyId().toString()
                    )
            );

            Instant now = Instant.now();
            existing.setRevokedAt(now);
            existing.setEnabled(false);
            apiKeyRepository.save(existing);

            String rawKey = apiKeyTokenService.generateRawKey();
            String keyHash = apiKeyTokenService.hashHmac(rawKey);
            String prefix = buildPrefix(rawKey);

            ApiKey rotated = new ApiKey();
            rotated.setTenantId(tenantId);
            rotated.setKeyName(existing.getKeyName());
            rotated.setApiKeyHash(keyHash);
            rotated.setEnabled(true);
            rotated.setExpiresAt(effectiveExpiresAt);
            rotated.setKeyVersion(existing.getKeyVersion() + 1);
            rotated.setRotatedFromApiKeyId(existing.getApiKeyId());
            rotated.setPrefix(prefix);
            rotated.setHashAlg("HMAC_SHA256");
            rotated.setCreatedBy(TenantContextHolder.getUserId());

            apiKeyRepository.save(rotated);

            ApiKeyRotateResponse response = buildRotateResponse(rotated, existing.getApiKeyId(), rawKey);
            writeAuditOutboxRotate(existing, rotated, response, evidenceId);

            if (existingKey == null && idempotencyKey != null && !idempotencyKey.isBlank()) {
                String aad = buildIdempotencyAad(tenantId, scope, idempotencyKey);
                InviteTokenCrypto.EncryptedToken encrypted = inviteTokenCrypto.encrypt(rawKey, aad);
                String responseJson = serializeRotateResponse(response, encrypted);

                IdempotencyKeyEntity entity = new IdempotencyKeyEntity();
                entity.setId(new IdempotencyKeyId(tenantId, scope, idempotencyKey));
                entity.setRequestHash(requestHash);
                entity.setResponseJson(responseJson);
                entity.setExpiresAt(resolveIdempotencyExpiry(effectiveExpiresAt));
                idempotencyKeyRepository.save(entity);
            }

            return response;
        });
    }

    public ApiKeyRevokeResponse revokeApiKey(UUID apiKeyId, ApiKeyRevokeRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_CONTEXT_REQUIRED");
        }
        ensureTenantActive(tenantId);

        return transactionTemplate.execute(status -> {
            ApiKey apiKey = apiKeyRepository.findByIdForUpdate(apiKeyId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API_KEY_NOT_FOUND"));

            if (!tenantId.equals(apiKey.getTenantId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CROSS_TENANT_FORBIDDEN");
            }

            if (apiKey.getRevokedAt() != null) {
                ApiKeyRevokeResponse response = new ApiKeyRevokeResponse();
                response.setApiKeyId(apiKey.getApiKeyId());
                response.setRevokedAt(apiKey.getRevokedAt());
                return response;
            }

                Map<String, Object> revokeMetadata = new LinkedHashMap<>();
                revokeMetadata.put("tenantId", tenantId.toString());
                revokeMetadata.put("apiKeyId", apiKey.getApiKeyId().toString());
                revokeMetadata.put("reason", request != null ? request.getReason() : null);

                UUID evidenceId = createEvidence(
                    tenantId,
                    "API_KEY_REVOKED",
                    "API_KEY_REVOKED",
                    "API key revoked",
                    revokeMetadata
                );

            Instant revokedAt = Instant.now();
            apiKey.setRevokedAt(revokedAt);
            apiKey.setEnabled(false);
            apiKeyRepository.save(apiKey);

            writeAuditOutboxRevoke(apiKey, request != null ? request.getReason() : null, evidenceId);

            ApiKeyRevokeResponse response = new ApiKeyRevokeResponse();
            response.setApiKeyId(apiKey.getApiKeyId());
            response.setRevokedAt(revokedAt);
            return response;
        });
    }

    private String normalizeKeyName(String name) {
        if (name == null || name.isBlank()) {
            return "api-key";
        }
        return name.trim();
    }

    private Instant resolveExpiresAt(Instant expiresAt, Integer expiresInDays) {
        if (expiresAt != null) {
            if (expiresAt.isBefore(Instant.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_EXPIRES_AT");
            }
            return expiresAt;
        }
        if (expiresInDays != null) {
            if (expiresInDays <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_EXPIRES_IN_DAYS");
            }
            return Instant.now().plus(expiresInDays, ChronoUnit.DAYS);
        }
        return null;
    }

    private void ensureTenantActive(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_NOT_FOUND"));
        if (!TenantStatuses.ACTIVE.equals(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_NOT_ACTIVE");
        }
    }

    private String computeRequestHash(UUID tenantId, String keyName, Instant expiresAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("keyName", keyName);
        payload.put("expiresAt", expiresAt != null ? expiresAt.toString() : null);
        JsonNode canonical = CanonicalJson.canonicalize(objectMapper, objectMapper.valueToTree(payload));
        String canonicalJson = CanonicalJson.writeCanonicalJson(objectMapper, canonical);
        return sha256(canonicalJson);
    }

    private String computeRotateRequestHash(UUID tenantId, UUID apiKeyId, Instant expiresAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("apiKeyId", apiKeyId.toString());
        payload.put("expiresAt", expiresAt != null ? expiresAt.toString() : null);
        JsonNode canonical = CanonicalJson.canonicalize(objectMapper, objectMapper.valueToTree(payload));
        String canonicalJson = CanonicalJson.writeCanonicalJson(objectMapper, canonical);
        return sha256(canonicalJson);
    }

    private ApiKeyCreateResponse responseFromIdempotency(IdempotencyKeyEntity entity) {
        try {
            JsonNode node = objectMapper.readTree(entity.getResponseJson());
            String tokenIv = node.path("tokenIv").asText(null);
            String tokenCiphertext = node.path("tokenCiphertext").asText(null);
            String token = null;
            if (tokenIv != null && tokenCiphertext != null) {
                String aad = buildIdempotencyAad(entity.getId().getTenantId(), entity.getId().getScope(), entity.getId().getIdempotencyKey());
                token = inviteTokenCrypto.decrypt(tokenIv, tokenCiphertext, aad);
            }
            ApiKeyCreateResponse response = new ApiKeyCreateResponse();
            response.setApiKeyId(UUID.fromString(node.get("apiKeyId").asText()));
            response.setTenantId(UUID.fromString(node.get("tenantId").asText()));
            response.setKeyName(node.get("keyName").asText());
            response.setPrefix(node.get("prefix").asText());
            if (!node.get("expiresAt").isNull()) {
                response.setExpiresAt(Instant.parse(node.get("expiresAt").asText()));
            }
            response.setApiKey(token);
            return response;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCY_REPLAY_FAILED", ex);
        }
    }

    private ApiKeyRotateResponse responseFromRotateIdempotency(IdempotencyKeyEntity entity) {
        try {
            JsonNode node = objectMapper.readTree(entity.getResponseJson());
            String tokenIv = node.path("tokenIv").asText(null);
            String tokenCiphertext = node.path("tokenCiphertext").asText(null);
            String token = null;
            if (tokenIv != null && tokenCiphertext != null) {
                String aad = buildIdempotencyAad(entity.getId().getTenantId(), entity.getId().getScope(), entity.getId().getIdempotencyKey());
                token = inviteTokenCrypto.decrypt(tokenIv, tokenCiphertext, aad);
            }
            ApiKeyRotateResponse response = new ApiKeyRotateResponse();
            response.setApiKeyId(UUID.fromString(node.get("apiKeyId").asText()));
            response.setRotatedFromApiKeyId(UUID.fromString(node.get("rotatedFromApiKeyId").asText()));
            response.setPrefix(node.get("prefix").asText());
            if (!node.get("expiresAt").isNull()) {
                response.setExpiresAt(Instant.parse(node.get("expiresAt").asText()));
            }
            response.setApiKey(token);
            return response;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCY_REPLAY_FAILED", ex);
        }
    }

    private String serializeCreateResponse(ApiKeyCreateResponse response, InviteTokenCrypto.EncryptedToken encrypted) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("apiKeyId", response.getApiKeyId().toString());
        payload.put("tenantId", response.getTenantId().toString());
        payload.put("keyName", response.getKeyName());
        payload.put("prefix", response.getPrefix());
        payload.put("expiresAt", response.getExpiresAt() != null ? response.getExpiresAt().toString() : null);
        payload.put("tokenIv", encrypted.ivBase64());
        payload.put("tokenCiphertext", encrypted.ciphertextBase64());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize idempotency response", ex);
        }
    }

    private String serializeRotateResponse(ApiKeyRotateResponse response, InviteTokenCrypto.EncryptedToken encrypted) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("apiKeyId", response.getApiKeyId().toString());
        payload.put("rotatedFromApiKeyId", response.getRotatedFromApiKeyId().toString());
        payload.put("prefix", response.getPrefix());
        payload.put("expiresAt", response.getExpiresAt() != null ? response.getExpiresAt().toString() : null);
        payload.put("tokenIv", encrypted.ivBase64());
        payload.put("tokenCiphertext", encrypted.ciphertextBase64());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize idempotency response", ex);
        }
    }

    private ApiKeyCreateResponse buildCreateResponse(ApiKey apiKey, String rawKey) {
        ApiKeyCreateResponse response = new ApiKeyCreateResponse();
        response.setApiKeyId(apiKey.getApiKeyId());
        response.setTenantId(apiKey.getTenantId());
        response.setKeyName(apiKey.getKeyName());
        response.setPrefix(apiKey.getPrefix());
        response.setExpiresAt(apiKey.getExpiresAt());
        response.setApiKey(rawKey);
        return response;
    }

    private ApiKeyRotateResponse buildRotateResponse(ApiKey apiKey, UUID rotatedFromId, String rawKey) {
        ApiKeyRotateResponse response = new ApiKeyRotateResponse();
        response.setApiKeyId(apiKey.getApiKeyId());
        response.setRotatedFromApiKeyId(rotatedFromId);
        response.setPrefix(apiKey.getPrefix());
        response.setExpiresAt(apiKey.getExpiresAt());
        response.setApiKey(rawKey);
        return response;
    }

    private String buildPrefix(String rawKey) {
        int length = Math.min(8, rawKey.length());
        return rawKey.substring(0, length);
    }

    private void writeAuditOutboxCreate(ApiKey apiKey, ApiKeyCreateResponse response, UUID evidenceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", apiKey.getTenantId().toString());
        payload.put("apiKeyId", apiKey.getApiKeyId().toString());
        payload.put("keyName", apiKey.getKeyName());
        payload.put("prefix", apiKey.getPrefix());
        payload.put("expiresAt", apiKey.getExpiresAt());
        payload.put("keyVersion", apiKey.getKeyVersion());
        payload.put("actorUserId", Optional.ofNullable(TenantContextHolder.getUserId()).map(UUID::toString).orElse(null));
        payload.put("actorRoles", TenantContextHolder.getRoles());
        payload.put("requestId", TenantContextHolder.getRequestId());
        payload.put("traceId", TenantContextHolder.getTraceId());
        writeAuditOutbox("API_KEY_CREATED", apiKey.getTenantId(), apiKey.getApiKeyId().toString(), payload, evidenceId);
    }

    private void writeAuditOutboxRotate(ApiKey oldKey, ApiKey newKey, ApiKeyRotateResponse response, UUID evidenceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", newKey.getTenantId().toString());
        payload.put("oldApiKeyId", oldKey.getApiKeyId().toString());
        payload.put("newApiKeyId", newKey.getApiKeyId().toString());
        payload.put("oldVersion", oldKey.getKeyVersion());
        payload.put("newVersion", newKey.getKeyVersion());
        payload.put("prefix", newKey.getPrefix());
        payload.put("expiresAt", newKey.getExpiresAt());
        payload.put("actorUserId", Optional.ofNullable(TenantContextHolder.getUserId()).map(UUID::toString).orElse(null));
        payload.put("actorRoles", TenantContextHolder.getRoles());
        payload.put("requestId", TenantContextHolder.getRequestId());
        payload.put("traceId", TenantContextHolder.getTraceId());
        writeAuditOutbox("API_KEY_ROTATED", newKey.getTenantId(), newKey.getApiKeyId().toString(), payload, evidenceId);
    }

    private void writeAuditOutboxRevoke(ApiKey apiKey, String reason, UUID evidenceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", apiKey.getTenantId().toString());
        payload.put("apiKeyId", apiKey.getApiKeyId().toString());
        payload.put("reason", reason);
        payload.put("revokedAt", apiKey.getRevokedAt());
        payload.put("actorUserId", Optional.ofNullable(TenantContextHolder.getUserId()).map(UUID::toString).orElse(null));
        payload.put("actorRoles", TenantContextHolder.getRoles());
        payload.put("requestId", TenantContextHolder.getRequestId());
        payload.put("traceId", TenantContextHolder.getTraceId());
        writeAuditOutbox("API_KEY_REVOKED", apiKey.getTenantId(), apiKey.getApiKeyId().toString(), payload, evidenceId);
    }

    private void writeAuditOutbox(String action, UUID tenantId, String entityId, Map<String, Object> payload, UUID evidenceId) {
        if (evidenceId != null) {
            payload.put("evidenceId", evidenceId.toString());
        }
        JsonNode payloadNode = objectMapper.valueToTree(payload);
        JsonNode canonicalPayload = CanonicalJson.canonicalize(objectMapper, payloadNode);
        String canonicalJson = CanonicalJson.writeCanonicalJson(objectMapper, canonicalPayload);
        Instant occurredAt = Instant.now();
        String payloadHash = sha256(action + "|" + tenantId + "|" + occurredAt + "|" + canonicalJson);

        AuditEvent.ActorType actorType = TenantContextHolder.getUserId() != null
                ? AuditEvent.ActorType.USER
                : AuditEvent.ActorType.SYSTEM;

        AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(TenantContextHolder.getUserId())
                .actorType(actorType)
                .service(serviceName)
                .action(action)
                .entityType("API_KEY")
                .entityId(entityId)
                .payloadHash(payloadHash)
                .evidenceId(evidenceId)
                .timestamp(occurredAt)
                .metadata(canonicalPayload)
                .build();

        auditWriter.write(auditEvent);

        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setEventType(action);
        envelope.setTenantId(tenantId);
        envelope.setActorId(TenantContextHolder.getUserId());
        envelope.setActorType(TenantContextHolder.getUserId() != null ? ActorType.USER : ActorType.SYSTEM);
        envelope.setSourceService(serviceName);
        envelope.setEntityType("API_KEY");
        envelope.setEntityId(entityId);
        envelope.setOccurredAt(occurredAt);
        envelope.setCorrelationId(resolveCorrelationId());
        envelope.setPayload(canonicalPayload);
        envelope.setPayloadHash(payloadHash);
        envelope.setIdempotencyKey(null);

        outboxWriter.write(envelope);
    }

    private String resolveCorrelationId() {
        if (TenantContextHolder.getRequestId() != null) {
            return TenantContextHolder.getRequestId();
        }
        if (TenantContextHolder.getTraceId() != null) {
            return TenantContextHolder.getTraceId();
        }
        return UUID.randomUUID().toString();
    }

    private UUID createEvidence(UUID tenantId, String eventType, String evidenceType, String description, Map<String, Object> metadata) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", Optional.ofNullable(TenantContextHolder.getUserId()).map(UUID::toString).orElse(null));
        payload.put("eventType", eventType);
        payload.put("evidenceType", evidenceType);
        payload.put("description", description);
        Map<String, Object> safeMetadata = new LinkedHashMap<>();
        safeMetadata.put("tenantId", tenantId.toString());
        safeMetadata.put("requestId", TenantContextHolder.getRequestId());
        safeMetadata.put("traceId", TenantContextHolder.getTraceId());
        if (metadata != null) {
            safeMetadata.putAll(metadata);
        }
        payload.put("metadata", safeMetadata);
        try {
            return evidenceClient.createEvidence(payload);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "EVIDENCE_UNAVAILABLE", ex);
        }
    }

    private boolean isIdempotencyExpired(IdempotencyKeyEntity entity) {
        Instant expiresAt = entity.getExpiresAt();
        return expiresAt != null && !expiresAt.isAfter(Instant.now());
    }

    private Instant resolveIdempotencyExpiry(Instant resourceExpiresAt) {
        Instant ttlExpiry = Instant.now().plus(IDEMPOTENCY_TTL_DAYS, ChronoUnit.DAYS);
        if (resourceExpiresAt != null && resourceExpiresAt.isBefore(ttlExpiry)) {
            return resourceExpiresAt;
        }
        return ttlExpiry;
    }

    private String buildIdempotencyAad(UUID tenantId, String scope, String idempotencyKey) {
        return tenantId + "|" + scope + "|" + idempotencyKey;
    }

    private String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
