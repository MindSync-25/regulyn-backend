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
import io.regulyn.identity.entity.Tenant;
import io.regulyn.identity.entity.TenantMonthlyUsage;
import io.regulyn.identity.entity.TenantPlanLimits;
import io.regulyn.identity.repository.TenantMonthlyUsageRepository;
import io.regulyn.identity.repository.TenantPlanLimitsRepository;
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
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TenantReadOnlyEvaluator {

    private final TenantRepository tenantRepository;
    private final TenantPlanLimitsRepository planLimitsRepository;
    private final TenantMonthlyUsageRepository monthlyUsageRepository;
    private final UserRepository userRepository;
    private final EvidenceClient evidenceClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final String serviceName;

    public TenantReadOnlyEvaluator(TenantRepository tenantRepository,
                                   TenantPlanLimitsRepository planLimitsRepository,
                                   TenantMonthlyUsageRepository monthlyUsageRepository,
                                   UserRepository userRepository,
                                   EvidenceClient evidenceClient,
                                   AuditWriter auditWriter,
                                   OutboxWriter outboxWriter,
                                   ObjectMapper objectMapper,
                                   PlatformTransactionManager transactionManager,
                                   @Value("${spring.application.name:identity-tenant-service}") String serviceName) {
        this.tenantRepository = tenantRepository;
        this.planLimitsRepository = planLimitsRepository;
        this.monthlyUsageRepository = monthlyUsageRepository;
        this.userRepository = userRepository;
        this.evidenceClient = evidenceClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.serviceName = serviceName;
    }

    public void evaluateAndUpdate(UUID tenantId, String trigger) {
        if (tenantId == null) {
            return;
        }

        transactionTemplate.execute(status -> {
            Tenant tenant = tenantRepository.findByTenantIdForUpdate(tenantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_NOT_FOUND"));

            if (!TenantStatuses.ACTIVE.equals(tenant.getStatus())) {
                return null;
            }

            TenantPlanLimits limits = planLimitsRepository.findByTenantId(tenantId)
                    .orElseGet(() -> {
                        TenantPlanLimits defaults = new TenantPlanLimits();
                        defaults.setTenantId(tenantId);
                        return defaults;
                    });

            int yearMonth = currentYearMonth();
            TenantMonthlyUsage usage = monthlyUsageRepository.findByTenantIdAndYearMonth(tenantId, yearMonth)
                    .orElse(null);

            long enabledUsers = userRepository.countByTenantIdAndEnabledTrue(tenantId);
            int dsarCount = usage != null ? usage.getDsarCount() : 0;
            int exportCount = usage != null ? usage.getExportCount() : 0;

            Evaluation evaluation = evaluate(limits, enabledUsers, dsarCount, exportCount);

            boolean currentlyReadOnly = Boolean.TRUE.equals(tenant.getReadOnly());
            String currentReason = tenant.getReadOnlyReason();

            if (evaluation.readOnly == currentlyReadOnly &&
                (evaluation.reason == null || evaluation.reason.equals(currentReason))) {
                return null;
            }

            String eventType = evaluation.readOnly ? "TENANT_READ_ONLY_ENABLED" : "TENANT_READ_ONLY_DISABLED";
            UUID evidenceId = createEvidence(tenantId, eventType, eventType, "Tenant read-only evaluation", Map.of(
                    "tenantId", tenantId.toString(),
                    "trigger", trigger,
                    "reason", evaluation.reason
            ));

            Instant now = Instant.now();
            Boolean previousReadOnly = tenant.getReadOnly();
            String previousReason = tenant.getReadOnlyReason();
            Instant previousSince = tenant.getReadOnlySince();

            tenant.setReadOnly(evaluation.readOnly);
            tenant.setReadOnlyReason(evaluation.reason);
            tenant.setReadOnlySince(evaluation.readOnly ? now : null);
            tenantRepository.save(tenant);

            writeAuditOutbox(eventType, tenantId, previousReadOnly, evaluation.readOnly,
                    previousReason, evaluation.reason, previousSince, tenant.getReadOnlySince(), evidenceId);

            return null;
        });
    }

    private Evaluation evaluate(TenantPlanLimits limits, long enabledUsers, int dsarCount, int exportCount) {
        if (limits.getMaxUsers() != null && enabledUsers >= limits.getMaxUsers()) {
            return new Evaluation(true, "LIMIT_MAX_USERS_EXCEEDED");
        }
        if (limits.getDsarPerMonth() != null && dsarCount >= limits.getDsarPerMonth()) {
            return new Evaluation(true, "LIMIT_DSAR_EXCEEDED");
        }
        if (limits.getExportsPerMonth() != null && exportCount >= limits.getExportsPerMonth()) {
            return new Evaluation(true, "LIMIT_EXPORTS_EXCEEDED");
        }
        return new Evaluation(false, null);
    }

    private int currentYearMonth() {
        YearMonth ym = YearMonth.now(ZoneOffset.UTC);
        return ym.getYear() * 100 + ym.getMonthValue();
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

    private void writeAuditOutbox(String action,
                                  UUID tenantId,
                                  Boolean previousReadOnly,
                                  Boolean newReadOnly,
                                  String previousReason,
                                  String newReason,
                                  Instant previousSince,
                                  Instant newSince,
                                  UUID evidenceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("previousReadOnly", previousReadOnly);
        payload.put("newReadOnly", newReadOnly);
        payload.put("previousReason", previousReason);
        payload.put("newReason", newReason);
        payload.put("previousSince", previousSince);
        payload.put("newSince", newSince);
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

    private record Evaluation(boolean readOnly, String reason) {
    }
}
