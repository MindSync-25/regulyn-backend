package com.regulyn.retention.cascade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CascadeEventWriter {

    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final String serviceName;

    public CascadeEventWriter(
            AuditWriter auditWriter,
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper,
            @Value("${spring.application.name:retention-deletion-service}") String serviceName) {
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }

    public void writeAudit(UUID tenantId, UUID actorId, String action, String entityType, UUID entityId, Map<String, Object> payload) {
        String payloadJson = toCanonicalJson(payload);
        String payloadHash = sha256Hex(payloadJson.getBytes(StandardCharsets.UTF_8));
        JsonNode metadata = objectMapper.valueToTree(payload);

        AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(actorId)
                .actorType(actorId != null ? AuditEvent.ActorType.USER : AuditEvent.ActorType.SYSTEM)
                .action(action)
                .entityType(entityType)
                .entityId(entityId.toString())
                .payloadHash(payloadHash)
                .metadata(metadata)
                .build();

        auditWriter.write(auditEvent);
    }

    public void writeOutbox(UUID tenantId, UUID actorId, String eventType, String entityType, UUID entityId, Object payload, String idempotencyKey) {
        withTenantContext(tenantId, actorId, () -> {
            EventEnvelopeV1 event = EventFactory.create(
                    eventType,
                    serviceName,
                    entityType,
                    entityId.toString(),
                    payload,
                    idempotencyKey
            );
            outboxWriter.write(event);
        });
    }

    private void withTenantContext(UUID tenantId, UUID actorId, Runnable action) {
        TenantContext context = new TenantContext();
        context.setTenantId(tenantId);
        context.setUserId(actorId);
        context.setRoles(Set.of());
        TenantContextHolder.setContext(context);
        try {
            action.run();
        } finally {
            TenantContextHolder.clear();
        }
    }

    private String toCanonicalJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize payload", e);
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute SHA-256 hash", e);
        }
    }
}
