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
import io.regulyn.identity.dto.LockUserResponse;
import io.regulyn.identity.dto.UnlockUserResponse;
import io.regulyn.identity.entity.Tenant;
import io.regulyn.identity.entity.User;
import io.regulyn.identity.repository.TenantRepository;
import io.regulyn.identity.repository.UserRepository;
import io.regulyn.identity.util.CanonicalJson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final EvidenceClient evidenceClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final String serviceName;

    public UserAdminService(UserRepository userRepository,
                            TenantRepository tenantRepository,
                            EvidenceClient evidenceClient,
                            AuditWriter auditWriter,
                            OutboxWriter outboxWriter,
                            ObjectMapper objectMapper,
                            PlatformTransactionManager transactionManager,
                            @Value("${spring.application.name:identity-tenant-service}") String serviceName) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.evidenceClient = evidenceClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.serviceName = serviceName;
    }

    public LockUserResponse lockUser(UUID userId, String reason) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_CONTEXT_REQUIRED");
        }
        // Note: No tenant status check - lock/unlock should work regardless of tenant status

        return transactionTemplate.execute(status -> {
            User user = userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

            if (!tenantId.equals(user.getTenantId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CROSS_TENANT_FORBIDDEN");
            }

            if (user.getLockedAt() != null) {
                // Keep enabled flag consistent with locked state
                if (Boolean.TRUE.equals(user.getEnabled())) {
                    user.setEnabled(false);
                    userRepository.save(user);
                }
                LockUserResponse response = new LockUserResponse();
                response.setUserId(user.getUserId());
                response.setLockedAt(user.getLockedAt());
                response.setLockedReason(user.getLockedReason());
                return response;
            }

            UUID evidenceId = createEvidence(
                    tenantId,
                    "USER_LOCKED",
                    "USER_LOCKED",
                    "User locked",
                    Map.of(
                            "tenantId", tenantId.toString(),
                            "userId", user.getUserId().toString(),
                            "reason", Optional.ofNullable(reason).orElse("")
                    )
            );

            Instant lockedAt = Instant.now();
            user.setLockedAt(lockedAt);
            user.setLockedReason(reason);
            user.setLockedByUserId(TenantContextHolder.getUserId());
            // Disable login when locked
            user.setEnabled(false);
            userRepository.save(user);

            writeAuditOutboxLock(user, null, lockedAt, reason, evidenceId);

            LockUserResponse response = new LockUserResponse();
            response.setUserId(user.getUserId());
            response.setLockedAt(lockedAt);
            response.setLockedReason(reason);
            return response;
        });
    }

    public UnlockUserResponse unlockUser(UUID userId, String reason) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_CONTEXT_REQUIRED");
        }
        // Note: No tenant status check - lock/unlock should work regardless of tenant status

        return transactionTemplate.execute(status -> {
            User user = userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

            if (!tenantId.equals(user.getTenantId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CROSS_TENANT_FORBIDDEN");
            }

            if (user.getLockedAt() == null) {
                // Ensure enabled flag is consistent with unlocked state
                if (!Boolean.TRUE.equals(user.getEnabled())) {
                    user.setEnabled(true);
                    userRepository.save(user);
                    UnlockUserResponse response = new UnlockUserResponse();
                    response.setUserId(user.getUserId());
                    response.setUnlockedAt(Instant.now());
                    return response;
                }
                UnlockUserResponse response = new UnlockUserResponse();
                response.setUserId(user.getUserId());
                response.setUnlockedAt(null);
                return response;
            }

            Instant previousLockedAt = user.getLockedAt();

            UUID evidenceId = createEvidence(
                    tenantId,
                    "USER_UNLOCKED",
                    "USER_UNLOCKED",
                    "User unlocked",
                    Map.of(
                            "tenantId", tenantId.toString(),
                            "userId", user.getUserId().toString(),
                            "reason", Optional.ofNullable(reason).orElse("")
                    )
            );

            user.setLockedAt(null);
            user.setLockedReason(null);
            user.setLockedByUserId(null);
            // Re-enable login when unlocked
            user.setEnabled(true);
            userRepository.save(user);

            writeAuditOutboxUnlock(user, previousLockedAt, reason, evidenceId);

            UnlockUserResponse response = new UnlockUserResponse();
            response.setUserId(user.getUserId());
            response.setUnlockedAt(Instant.now());
            return response;
        });
    }

    private void ensureTenantActive(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_NOT_FOUND"));
        // Allow lock/unlock operations for ACTIVE and SUSPENDED tenants (but not DELETED or DRAFT)
        if (TenantStatuses.DELETED.equals(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_DELETED");
        }
        if (TenantStatuses.DRAFT.equals(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_NOT_ACTIVE");
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

    private void writeAuditOutboxLock(User user, Instant previousLockedAt, Instant newLockedAt, String reason, UUID evidenceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", user.getTenantId().toString());
        payload.put("userId", user.getUserId().toString());
        payload.put("previousLockedAt", previousLockedAt);
        payload.put("newLockedAt", newLockedAt);
        payload.put("reason", reason);
        payload.put("actorUserId", Optional.ofNullable(TenantContextHolder.getUserId()).map(UUID::toString).orElse(null));
        payload.put("actorRoles", TenantContextHolder.getRoles());
        payload.put("requestId", TenantContextHolder.getRequestId());
        payload.put("traceId", TenantContextHolder.getTraceId());
        writeAuditOutbox("USER_LOCKED", user.getTenantId(), user.getUserId().toString(), payload, evidenceId);
    }

    private void writeAuditOutboxUnlock(User user, Instant previousLockedAt, String reason, UUID evidenceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", user.getTenantId().toString());
        payload.put("userId", user.getUserId().toString());
        payload.put("previousLockedAt", previousLockedAt);
        payload.put("newLockedAt", null);
        payload.put("reason", reason);
        payload.put("actorUserId", Optional.ofNullable(TenantContextHolder.getUserId()).map(UUID::toString).orElse(null));
        payload.put("actorRoles", TenantContextHolder.getRoles());
        payload.put("requestId", TenantContextHolder.getRequestId());
        payload.put("traceId", TenantContextHolder.getTraceId());
        writeAuditOutbox("USER_UNLOCKED", user.getTenantId(), user.getUserId().toString(), payload, evidenceId);
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
                .entityType("USER")
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
        envelope.setEntityType("USER");
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
