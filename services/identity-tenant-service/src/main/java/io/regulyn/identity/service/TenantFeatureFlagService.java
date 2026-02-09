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
import io.regulyn.identity.dto.TenantFeatureFlagRequest;
import io.regulyn.identity.dto.TenantFeatureFlagResponse;
import io.regulyn.identity.entity.Tenant;
import io.regulyn.identity.entity.TenantFeatureFlag;
import io.regulyn.identity.repository.TenantFeatureFlagRepository;
import io.regulyn.identity.repository.TenantRepository;
import io.regulyn.identity.util.CanonicalJson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TenantFeatureFlagService {

    private final TenantRepository tenantRepository;
    private final TenantFeatureFlagRepository featureFlagRepository;
    private final EvidenceClient evidenceClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final String serviceName;

    public TenantFeatureFlagService(TenantRepository tenantRepository,
                                    TenantFeatureFlagRepository featureFlagRepository,
                                    EvidenceClient evidenceClient,
                                    AuditWriter auditWriter,
                                    OutboxWriter outboxWriter,
                                    ObjectMapper objectMapper,
                                    PlatformTransactionManager transactionManager,
                                    @Value("${spring.application.name:identity-tenant-service}") String serviceName) {
        this.tenantRepository = tenantRepository;
        this.featureFlagRepository = featureFlagRepository;
        this.evidenceClient = evidenceClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.serviceName = serviceName;
    }

    public List<TenantFeatureFlagResponse> getFeatureFlags() {
        UUID tenantId = requireTenantId();
        ensureTenantActive(tenantId);

        List<TenantFeatureFlag> flags = featureFlagRepository.findByTenantId(tenantId);
        return flags.stream().map(this::toResponse).toList();
    }

    public TenantFeatureFlagResponse upsertFeatureFlag(String flagKey, TenantFeatureFlagRequest request) {
        UUID tenantId = requireTenantId();
        ensureTenantActive(tenantId);

        if (flagKey == null || flagKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FLAG_KEY_REQUIRED");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FEATURE_FLAG_REQUEST_REQUIRED");
        }

        Map<String, Object> evidenceMetadata = new LinkedHashMap<>();
        evidenceMetadata.put("tenantId", tenantId.toString());
        evidenceMetadata.put("flagKey", flagKey);
        evidenceMetadata.put("enabled", request.getEnabled());
        evidenceMetadata.put("value", request.getValue());

        UUID evidenceId = createEvidence(
                tenantId,
                "TENANT_FEATURE_FLAG_UPDATED",
                "TENANT_FEATURE_FLAG_UPDATED",
                "Tenant feature flag updated",
                evidenceMetadata
        );

        return transactionTemplate.execute(status -> {
            TenantFeatureFlag existing = featureFlagRepository.findByTenantIdAndFlagKey(tenantId, flagKey)
                    .orElse(null);

            Boolean previousEnabled = existing != null ? existing.getEnabled() : null;
            String previousValue = existing != null ? existing.getValueJson() : null;

            TenantFeatureFlag flag = existing != null ? existing : new TenantFeatureFlag();
            if (flag.getFeatureFlagId() == null) {
                flag.setFeatureFlagId(UUID.randomUUID());
            }
            flag.setTenantId(tenantId);
            flag.setFlagKey(flagKey);
            if (request.getEnabled() != null) {
                flag.setEnabled(request.getEnabled());
            }
            flag.setValueJson(serializeValue(request.getValue()));

            featureFlagRepository.save(flag);

            writeAuditOutbox(tenantId,
                    flagKey,
                    previousEnabled,
                    flag.getEnabled(),
                    previousValue,
                    flag.getValueJson(),
                    evidenceId);

            return toResponse(flag);
        });
    }

    private TenantFeatureFlagResponse toResponse(TenantFeatureFlag flag) {
        TenantFeatureFlagResponse response = new TenantFeatureFlagResponse();
        response.setFlagKey(flag.getFlagKey());
        response.setEnabled(flag.getEnabled());
        response.setValue(parseValue(flag.getValueJson()));
        return response;
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_CONTEXT_REQUIRED");
        }
        return tenantId;
    }

    private void ensureTenantActive(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_NOT_FOUND"));
        if (!TenantStatuses.ACTIVE.equals(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_NOT_ACTIVE");
        }
    }

    private String serializeValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FEATURE_FLAG_VALUE_INVALID", e);
        }
    }

    private JsonNode parseValue(String valueJson) {
        if (valueJson == null) {
            return null;
        }
        try {
            return objectMapper.readTree(valueJson);
        } catch (Exception e) {
            return null;
        }
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

    private void writeAuditOutbox(UUID tenantId,
                                  String flagKey,
                                  Boolean previousEnabled,
                                  Boolean newEnabled,
                                  String previousValue,
                                  String newValue,
                                  UUID evidenceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("flagKey", flagKey);
        payload.put("previousEnabled", previousEnabled);
        payload.put("newEnabled", newEnabled);
        payload.put("previousValue", previousValue);
        payload.put("newValue", newValue);
        payload.put("actorUserId", Optional.ofNullable(TenantContextHolder.getUserId()).map(UUID::toString).orElse(null));
        payload.put("actorRoles", TenantContextHolder.getRoles());
        payload.put("requestId", TenantContextHolder.getRequestId());
        payload.put("traceId", TenantContextHolder.getTraceId());
        if (evidenceId != null) {
            payload.put("evidenceId", evidenceId.toString());
        }

        JsonNode payloadNode = objectMapper.valueToTree(payload);
        JsonNode canonicalPayload = CanonicalJson.canonicalize(objectMapper, payloadNode);
        String canonicalJson = CanonicalJson.writeCanonicalJson(objectMapper, canonicalPayload);
        Instant occurredAt = Instant.now();
        String action = "TENANT_FEATURE_FLAG_UPDATED";
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
                .entityType("TENANT")
                .entityId(tenantId.toString())
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
        envelope.setEntityType("TENANT");
        envelope.setEntityId(tenantId.toString());
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