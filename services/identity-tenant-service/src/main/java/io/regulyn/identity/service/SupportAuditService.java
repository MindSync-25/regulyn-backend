package io.regulyn.identity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import io.regulyn.identity.dto.SupportAuditRequest;
import io.regulyn.identity.util.CanonicalJson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SupportAuditService {

    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    private final String serviceName;

    public SupportAuditService(AuditWriter auditWriter,
                               ObjectMapper objectMapper,
                               @Value("${spring.application.name:unknown-service}") String serviceName) {
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }

    public void recordSupportAudit(SupportAuditRequest request) {
        UUID tenantId = request.getTenantId();
        Instant occurredAt = Instant.now();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("action", request.getAction());
        payload.put("resourceType", request.getResourceType());
        payload.put("resourceId", request.getResourceId());
        payload.put("correlationId", resolveCorrelationId(request));
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            payload.put("summary", request.getNotes().length() <= 160
                ? request.getNotes()
                : request.getNotes().substring(0, 160));
        }
        payload.put("notes", request.getNotes());
        payload.put("actorUserId", Optional.ofNullable(TenantContextHolder.getUserId()).map(UUID::toString).orElse(null));
        payload.put("actorRoles", TenantContextHolder.getRoles());
        payload.put("requestId", TenantContextHolder.getRequestId());
        payload.put("traceId", TenantContextHolder.getTraceId());

        JsonNode payloadNode = objectMapper.valueToTree(payload);
        JsonNode canonicalPayload = CanonicalJson.canonicalize(objectMapper, payloadNode);
        String canonicalJson = CanonicalJson.writeCanonicalJson(objectMapper, canonicalPayload);
        String payloadHash = sha256(request.getAction() + "|" + tenantId + "|" + occurredAt + "|" + canonicalJson);

        String entityId = request.getResourceId() != null && !request.getResourceId().isBlank()
                ? request.getResourceId()
                : tenantId.toString();

        AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(TenantContextHolder.getUserId())
                .actorType(TenantContextHolder.getUserId() != null ? AuditEvent.ActorType.USER : AuditEvent.ActorType.SYSTEM)
                .service(serviceName)
                .action(request.getAction())
                .entityType(request.getResourceType())
                .entityId(entityId)
                .payloadHash(payloadHash)
                .timestamp(occurredAt)
                .metadata(canonicalPayload)
                .build();

        auditWriter.write(auditEvent);
    }

    private String resolveCorrelationId(SupportAuditRequest request) {
        if (request.getCorrelationId() != null && !request.getCorrelationId().isBlank()) {
            return request.getCorrelationId();
        }
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
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA256_FAILED", ex);
        }
    }
}
