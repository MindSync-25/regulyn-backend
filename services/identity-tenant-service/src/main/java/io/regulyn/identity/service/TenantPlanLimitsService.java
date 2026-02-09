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
import io.regulyn.identity.dto.TenantPlanLimitsRequest;
import io.regulyn.identity.dto.TenantPlanLimitsResponse;
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
public class TenantPlanLimitsService {

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

    public TenantPlanLimitsService(TenantRepository tenantRepository,
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

    public TenantPlanLimitsResponse getPlanLimits() {
        UUID tenantId = requireTenantId();
        ensureTenantActive(tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_NOT_FOUND"));

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

        return toResponse(tenant, limits, usage, yearMonth, enabledUsers);
    }

    public TenantPlanLimitsResponse updatePlanLimits(TenantPlanLimitsRequest request) {
        UUID tenantId = requireTenantId();
        ensureTenantActive(tenantId);

        validateRequest(request);

        Map<String, Object> evidenceMetadata = new LinkedHashMap<>();
        evidenceMetadata.put("tenantId", tenantId.toString());
        evidenceMetadata.put("maxUsers", request != null ? request.getMaxUsers() : null);
        evidenceMetadata.put("dsarPerMonth", request != null ? request.getDsarPerMonth() : null);
        evidenceMetadata.put("exportsPerMonth", request != null ? request.getExportsPerMonth() : null);

        UUID evidenceId = createEvidence(
            tenantId,
            "TENANT_PLAN_LIMITS_UPDATED",
            "TENANT_PLAN_LIMITS_UPDATED",
            "Tenant plan limits updated",
            evidenceMetadata
        );

        return transactionTemplate.execute(status -> {
            Tenant tenant = tenantRepository.findByTenantIdForUpdate(tenantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_NOT_FOUND"));

            TenantPlanLimits existing = planLimitsRepository.findByTenantId(tenantId).orElse(null);
            Integer previousMaxUsers = existing != null ? existing.getMaxUsers() : null;
            Integer previousDsarPerMonth = existing != null ? existing.getDsarPerMonth() : null;
            Integer previousExportsPerMonth = existing != null ? existing.getExportsPerMonth() : null;

            TenantPlanLimits limits = existing != null ? existing : new TenantPlanLimits();
            if (limits.getTenantId() == null) {
                limits.setTenantId(tenantId);
            }

            if (request != null && request.getMaxUsers() != null) {
                limits.setMaxUsers(request.getMaxUsers());
            }
            if (request != null && request.getDsarPerMonth() != null) {
                limits.setDsarPerMonth(request.getDsarPerMonth());
            }
            if (request != null && request.getExportsPerMonth() != null) {
                limits.setExportsPerMonth(request.getExportsPerMonth());
            }

            planLimitsRepository.save(limits);

            writeAuditOutbox(tenantId,
                    previousMaxUsers,
                    limits.getMaxUsers(),
                    previousDsarPerMonth,
                    limits.getDsarPerMonth(),
                    previousExportsPerMonth,
                    limits.getExportsPerMonth(),
                    evidenceId);

            int yearMonth = currentYearMonth();
            TenantMonthlyUsage usage = monthlyUsageRepository.findByTenantIdAndYearMonth(tenantId, yearMonth)
                    .orElse(null);
            long enabledUsers = userRepository.countByTenantIdAndEnabledTrue(tenantId);
            return toResponse(tenant, limits, usage, yearMonth, enabledUsers);
        });
    }

    private TenantPlanLimitsResponse toResponse(Tenant tenant,
                                               TenantPlanLimits limits,
                                               TenantMonthlyUsage usage,
                                               int yearMonth,
                                               long enabledUsers) {
        TenantPlanLimitsResponse response = new TenantPlanLimitsResponse();
        response.setMaxUsers(limits.getMaxUsers());
        response.setDsarPerMonth(limits.getDsarPerMonth());
        response.setExportsPerMonth(limits.getExportsPerMonth());
        response.setYearMonth(yearMonth);
        response.setDsarCount(usage != null ? usage.getDsarCount() : 0);
        response.setExportCount(usage != null ? usage.getExportCount() : 0);
        response.setEnabledUsers(enabledUsers);
        response.setReadOnly(tenant.getReadOnly());
        response.setReadOnlyReason(tenant.getReadOnlyReason());
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

    private void validateRequest(TenantPlanLimitsRequest request) {
        if (request == null) {
            return;
        }
        if (request.getMaxUsers() != null && request.getMaxUsers() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MAX_USERS_INVALID");
        }
        if (request.getDsarPerMonth() != null && request.getDsarPerMonth() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DSAR_PER_MONTH_INVALID");
        }
        if (request.getExportsPerMonth() != null && request.getExportsPerMonth() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EXPORTS_PER_MONTH_INVALID");
        }
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

    private void writeAuditOutbox(UUID tenantId,
                                  Integer previousMaxUsers,
                                  Integer newMaxUsers,
                                  Integer previousDsarPerMonth,
                                  Integer newDsarPerMonth,
                                  Integer previousExportsPerMonth,
                                  Integer newExportsPerMonth,
                                  UUID evidenceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("previousMaxUsers", previousMaxUsers);
        payload.put("newMaxUsers", newMaxUsers);
        payload.put("previousDsarPerMonth", previousDsarPerMonth);
        payload.put("newDsarPerMonth", newDsarPerMonth);
        payload.put("previousExportsPerMonth", previousExportsPerMonth);
        payload.put("newExportsPerMonth", newExportsPerMonth);
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
        String action = "TENANT_PLAN_LIMITS_UPDATED";
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