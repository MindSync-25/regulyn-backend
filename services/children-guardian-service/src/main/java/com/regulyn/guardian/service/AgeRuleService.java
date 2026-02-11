package com.regulyn.guardian.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.guardian.dto.AgeEvaluationResponse;
import com.regulyn.guardian.dto.AgeRuleEffectiveResponse;
import com.regulyn.guardian.dto.AgeRuleResponse;
import com.regulyn.guardian.dto.AgeRuleUpsertRequest;
import com.regulyn.guardian.entity.AgeThresholdRuleEntity;
import com.regulyn.guardian.repository.AgeThresholdRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AgeRuleService {

    public static final int GLOBAL_MIN_AGE = 13;
    public static final int GLOBAL_MAX_AGE = 18;

    private static final Logger logger = LoggerFactory.getLogger(AgeRuleService.class);
    private static final Set<String> PRIVILEGED_ROLES = Set.of("TENANT_ADMIN", "DPO", "REVIEWER");

    private final AgeThresholdRuleRepository ruleRepository;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;

    public AgeRuleService(
            AgeThresholdRuleRepository ruleRepository,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter) {
        this.ruleRepository = ruleRepository;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public AgeRuleResponse upsertRule(AgeRuleUpsertRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();
        Set<String> roles = context.getRoles();
        String actorRole = roles != null && !roles.isEmpty() ? roles.iterator().next() : null;

        if (actorRole == null || !PRIVILEGED_ROLES.contains(actorRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only TENANT_ADMIN, DPO, or REVIEWER can manage age rules");
        }

        String countryCode = normalizeCountry(request.countryCode());
        String stateCode = normalizeState(request.stateCode());
        short thresholdAgeYears = request.thresholdAgeYears();
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault());

        Optional<AgeThresholdRuleEntity> existingRule =
                ruleRepository.findByTenantIdAndRegionCountryCodeAndRegionStateCode(
                        tenantId,
                        countryCode,
                        stateCode
                );

        AgeThresholdRuleEntity rule = existingRule.orElseGet(AgeThresholdRuleEntity::new);
        boolean created = existingRule.isEmpty();

        if (created) {
            rule.setTenantId(tenantId);
            rule.setRegionCountryCode(countryCode);
            rule.setRegionStateCode(stateCode);
            rule.setMinLegalAgeYears((short) GLOBAL_MIN_AGE);
            rule.setMaxLegalAgeYears((short) GLOBAL_MAX_AGE);
            rule.setCreatedAt(Instant.now());
            rule.setCreatedBy(actorId);
        }

        short minLegal = rule.getMinLegalAgeYears() != null ? rule.getMinLegalAgeYears() : (short) GLOBAL_MIN_AGE;
        short maxLegal = rule.getMaxLegalAgeYears() != null ? rule.getMaxLegalAgeYears() : (short) GLOBAL_MAX_AGE;
        validateThreshold(thresholdAgeYears, minLegal, maxLegal);

        rule.setThresholdAgeYears(thresholdAgeYears);
        rule.setDefault(makeDefault);
        rule.setUpdatedAt(Instant.now());
        rule.setUpdatedBy(actorId);

        if (makeDefault) {
            Optional<AgeThresholdRuleEntity> currentDefault = ruleRepository.findByTenantIdAndIsDefaultTrue(tenantId);
            if (currentDefault.isPresent() && !currentDefault.get().getId().equals(rule.getId())) {
                AgeThresholdRuleEntity existingDefault = currentDefault.get();
                existingDefault.setDefault(false);
                existingDefault.setUpdatedAt(Instant.now());
                existingDefault.setUpdatedBy(actorId);
                AgeThresholdRuleEntity updatedDefault = ruleRepository.save(existingDefault);

                Map<String, Object> defaultPayload = new HashMap<>();
                defaultPayload.put("tenantId", tenantId.toString());
                defaultPayload.put("ruleId", updatedDefault.getId().toString());
                defaultPayload.put("countryCode", updatedDefault.getRegionCountryCode());
                defaultPayload.put("stateCode", updatedDefault.getRegionStateCode());
                defaultPayload.put("thresholdAgeYears", updatedDefault.getThresholdAgeYears());
                defaultPayload.put("isDefault", updatedDefault.isDefault());
                defaultPayload.put("actorUserId", actorId != null ? actorId.toString() : null);

                writeAudit(actorId, "AGE_RULE_UPDATED", "AgeThresholdRule", updatedDefault.getId(), defaultPayload);
                writeOutbox(tenantId, "age_rule.updated", "AgeThresholdRule", updatedDefault.getId().toString(), defaultPayload);
            }
        }

        AgeThresholdRuleEntity saved = ruleRepository.save(rule);

        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("ruleId", saved.getId().toString());
        payload.put("countryCode", saved.getRegionCountryCode());
        payload.put("stateCode", saved.getRegionStateCode());
        payload.put("thresholdAgeYears", saved.getThresholdAgeYears());
        payload.put("isDefault", saved.isDefault());
        payload.put("actorUserId", actorId != null ? actorId.toString() : null);

        if (created) {
            writeAudit(actorId, "AGE_RULE_CREATED", "AgeThresholdRule", saved.getId(), payload);
            writeOutbox(tenantId, "age_rule.created", "AgeThresholdRule", saved.getId().toString(), payload);
        } else {
            writeAudit(actorId, "AGE_RULE_UPDATED", "AgeThresholdRule", saved.getId(), payload);
            writeOutbox(tenantId, "age_rule.updated", "AgeThresholdRule", saved.getId().toString(), payload);
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AgeRuleResponse> listRules(UUID tenantId) {
        return ruleRepository.findByTenantIdOrderByIsDefaultDescRegionCountryCodeAscRegionStateCodeAsc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgeRuleEffectiveResponse resolveEffectiveThreshold(UUID tenantId, String countryCode, String stateCode) {
        AgeResolution resolution = resolveRule(tenantId, countryCode, stateCode);
        return new AgeRuleEffectiveResponse(resolution.thresholdAgeYears(), resolution.resolvedFrom().name());
    }

    @Transactional(readOnly = true)
    public AgeEvaluationResult evaluate(
            UUID tenantId,
            UUID childId,
            LocalDate dateOfBirth,
            String countryCode,
            String stateCode,
            LocalDate evaluationDate) {
        if (dateOfBirth == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DOB_REQUIRED");
        }
        String normalizedCountry = normalizeCountry(countryCode);
        String normalizedState = normalizeState(stateCode);
        LocalDate effectiveDate = evaluationDate != null ? evaluationDate : LocalDate.now();

        AgeResolution resolution = resolveRule(tenantId, normalizedCountry, normalizedState);
        int ageYears = Period.between(dateOfBirth, effectiveDate).getYears();
        boolean isMinor = ageYears < resolution.thresholdAgeYears();

        return new AgeEvaluationResult(
                tenantId,
                childId,
                dateOfBirth,
                normalizedCountry,
                normalizedState,
                resolution.thresholdAgeYears(),
                ageYears,
                isMinor,
                resolution.resolvedFrom(),
                effectiveDate
        );
    }

    public AgeEvaluationResponse publishAgeEvaluation(UUID actorId, AgeEvaluationResult result) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", result.tenantId().toString());
        if (result.childId() != null) {
            payload.put("childId", result.childId().toString());
        }
        payload.put("countryCode", result.regionCountryCode());
        payload.put("stateCode", result.regionStateCode());
        payload.put("thresholdAgeYears", result.thresholdAgeYears());
        payload.put("ageYears", result.ageYears());
        payload.put("isMinor", result.isMinor());
        payload.put("resolvedFrom", result.resolvedFrom().name());
        payload.put("evaluationDate", result.evaluationDate().toString());

        UUID entityId = result.childId() != null ? result.childId() : UUID.randomUUID();
        writeAudit(actorId, "CHILD_AGE_EVALUATED", "Child", entityId, payload);
        writeOutbox(result.tenantId(), "child.age_evaluated", "Child", entityId.toString(), payload);

        return new AgeEvaluationResponse(
                result.tenantId(),
                result.childId(),
                result.dateOfBirth(),
                result.regionCountryCode(),
                result.regionStateCode(),
                result.thresholdAgeYears(),
                result.ageYears(),
                result.isMinor(),
                result.resolvedFrom().name(),
                result.evaluationDate()
        );
    }

    private AgeResolution resolveRule(UUID tenantId, String countryCode, String stateCode) {
        String normalizedCountry = normalizeCountry(countryCode);
        String normalizedState = normalizeState(stateCode);

        if (normalizedState != null) {
            Optional<AgeThresholdRuleEntity> stateRule = ruleRepository
                    .findByTenantIdAndRegionCountryCodeAndRegionStateCode(tenantId, normalizedCountry, normalizedState);
            if (stateRule.isPresent()) {
                return new AgeResolution(stateRule.get().getThresholdAgeYears(), AgeResolvedFrom.STATE_RULE);
            }
        }

        Optional<AgeThresholdRuleEntity> countryRule = ruleRepository
                .findByTenantIdAndRegionCountryCodeAndRegionStateCode(tenantId, normalizedCountry, null);
        if (countryRule.isPresent()) {
            return new AgeResolution(countryRule.get().getThresholdAgeYears(), AgeResolvedFrom.COUNTRY_RULE);
        }

        Optional<AgeThresholdRuleEntity> defaultRule = ruleRepository.findByTenantIdAndIsDefaultTrue(tenantId);
        if (defaultRule.isPresent()) {
            return new AgeResolution(defaultRule.get().getThresholdAgeYears(), AgeResolvedFrom.TENANT_DEFAULT);
        }

        return new AgeResolution((short) GLOBAL_MAX_AGE, AgeResolvedFrom.PLATFORM_DEFAULT);
    }

    private AgeRuleResponse toResponse(AgeThresholdRuleEntity rule) {
        return new AgeRuleResponse(
                rule.getId(),
                rule.getRegionCountryCode(),
                rule.getRegionStateCode(),
                rule.getThresholdAgeYears(),
                rule.isDefault(),
                rule.getMinLegalAgeYears(),
                rule.getMaxLegalAgeYears(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }

    private void validateThreshold(short thresholdAgeYears, short minLegal, short maxLegal) {
        if (thresholdAgeYears < GLOBAL_MIN_AGE || thresholdAgeYears > GLOBAL_MAX_AGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "THRESHOLD_OUT_OF_GLOBAL_BOUNDS");
        }
        if (thresholdAgeYears < minLegal || thresholdAgeYears > maxLegal) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "THRESHOLD_OUT_OF_LEGAL_BOUNDS");
        }
    }

    private String normalizeCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_REQUIRED");
        }
        String normalized = countryCode.trim().toUpperCase();
        if (normalized.length() != 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_CODE_INVALID");
        }
        return normalized;
    }

    private String normalizeState(String stateCode) {
        if (stateCode == null || stateCode.isBlank()) {
            return null;
        }
        return stateCode.trim().toUpperCase();
    }

    private void writeAudit(UUID actorId, String action, String entityType, UUID entityId, Map<String, ?> details) {
        try {
            String hash = hashPayload(details.toString());
            TenantContext context = TenantContextHolder.getContext();

            AuditEvent event = AuditEvent.builder()
                    .tenantId(context.getTenantId())
                    .actorId(actorId)
                    .actorType(actorId != null ? AuditEvent.ActorType.USER : AuditEvent.ActorType.SYSTEM)
                    .service("children-guardian-service")
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId.toString())
                    .payloadHash(hash)
                    .build();

            auditWriter.write(event);
        } catch (Exception e) {
            logger.error("Failed to write audit", e);
        }
    }

    private void writeOutbox(UUID tenantId, String eventType, String entityType, String entityId, Map<String, ?> payload) {
        try {
            Map<String, Object> safePayload = new HashMap<>();
            payload.forEach((k, v) -> safePayload.put(k, v != null ? v.toString() : ""));

            EventEnvelopeV1 event = EventFactory.create(
                    eventType,
                    "children-guardian-service",
                    entityType,
                    entityId,
                    safePayload
            );

            outboxWriter.write(event);
        } catch (Exception e) {
            logger.error("Failed to write outbox event", e);
        }
    }

    private String hashPayload(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    public enum AgeResolvedFrom {
        STATE_RULE,
        COUNTRY_RULE,
        TENANT_DEFAULT,
        PLATFORM_DEFAULT
    }

    public record AgeEvaluationResult(
            UUID tenantId,
            UUID childId,
            LocalDate dateOfBirth,
            String regionCountryCode,
            String regionStateCode,
            short thresholdAgeYears,
            int ageYears,
            boolean isMinor,
            AgeResolvedFrom resolvedFrom,
            LocalDate evaluationDate
    ) {}

    private record AgeResolution(short thresholdAgeYears, AgeResolvedFrom resolvedFrom) {}
}
