package com.regulyn.guardian.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.guardian.client.NotificationClient;
import com.regulyn.guardian.entity.Child;
import com.regulyn.guardian.entity.ChildMajorityTransitionEntity;
import com.regulyn.guardian.entity.Guardian;
import com.regulyn.guardian.repository.ChildMajorityTransitionRepository;
import com.regulyn.guardian.repository.ChildRepository;
import com.regulyn.guardian.repository.GuardianConsentRepository;
import com.regulyn.guardian.repository.GuardianRepository;
import com.regulyn.guardian.service.ConsentStateMachine;
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
import java.util.*;

@Service
public class MajorityService {

    public static final String STATUS_TRANSITION_PENDING = "MAJORITY_TRANSITION_PENDING";
    public static final String STATUS_ADULT_CONSENT_REQUIRED = "ADULT_CONSENT_REQUIRED";

    private static final Logger logger = LoggerFactory.getLogger(MajorityService.class);

    private final AgeRuleService ageRuleService;
    private final ChildRepository childRepository;
    private final GuardianRepository guardianRepository;
    private final GuardianConsentRepository consentRepository;
    private final ChildMajorityTransitionRepository transitionRepository;
    private final NotificationClient notificationClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;

    public MajorityService(
            AgeRuleService ageRuleService,
            ChildRepository childRepository,
            GuardianRepository guardianRepository,
            GuardianConsentRepository consentRepository,
            ChildMajorityTransitionRepository transitionRepository,
            NotificationClient notificationClient,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter) {
        this.ageRuleService = ageRuleService;
        this.childRepository = childRepository;
        this.guardianRepository = guardianRepository;
        this.consentRepository = consentRepository;
        this.transitionRepository = transitionRepository;
        this.notificationClient = notificationClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
    }

    public LocalDate computeMajorityDate(LocalDate dob, short thresholdAgeYears) {
        return dob.plusYears(thresholdAgeYears);
    }

    @Transactional(readOnly = true)
    public MajorityEvaluationResult evaluateMajority(UUID tenantId, UUID childId, LocalDate evaluationDate) {
        Child child = childRepository.findByTenantIdAndChildId(tenantId, childId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Child not found"));

        String countryCode = resolveCountry(child);
        if (countryCode == null) {
            return MajorityEvaluationResult.error(childId, "INSUFFICIENT_REGION");
        }

        String stateCode = child.getRegionStateCode();
        AgeRuleService.AgeEvaluationResult evaluation = ageRuleService.evaluate(
                tenantId,
                childId,
                child.getDateOfBirth(),
                countryCode,
                stateCode,
                evaluationDate != null ? evaluationDate : LocalDate.now()
        );

        LocalDate majorityDate = computeMajorityDate(child.getDateOfBirth(), evaluation.thresholdAgeYears());
        boolean reached = !evaluation.evaluationDate().isBefore(majorityDate);

        return new MajorityEvaluationResult(
                tenantId,
                childId,
                child.getDateOfBirth(),
                evaluation.regionCountryCode(),
                evaluation.regionStateCode(),
                evaluation.thresholdAgeYears(),
                majorityDate,
                reached,
                evaluation.resolvedFrom().name(),
                evaluation.evaluationDate(),
                null
        );
    }

    @Transactional
    public MajorityTransitionResponse processMajorityTransition(UUID tenantId, UUID childId, LocalDate evaluationDate) {
        TenantContext context = TenantContextHolder.getContext();
        UUID actorId = context.getUserId();

        MajorityEvaluationResult evaluation = evaluateMajority(tenantId, childId, evaluationDate);
        if (evaluation.error() != null) {
            writeAudit(actorId, "CHILD_MAJORITY_EVALUATION_FAILED", "Child", childId,
                    Map.of("error", evaluation.error()));
            return MajorityTransitionResponse.fromEvaluation(evaluation, null);
        }

        Child child = childRepository.findByTenantIdAndChildId(tenantId, childId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Child not found"));

        ChildMajorityTransitionEntity transition = upsertTransition(tenantId, child, evaluation);

        if (evaluation.reached()) {
            if (!child.isGuardianAuthorityRevoked()) {
                child.setGuardianAuthorityRevoked(true);
                childRepository.save(child);
                writeAudit(actorId, "GUARDIAN_AUTHORITY_REVOKED_DUE_TO_MAJORITY", "Child", childId,
                        transitionPayload(evaluation, transition.getTransitionStatus()));
                writeOutbox(tenantId, "child.guardian_authority_revoked", "Child", childId.toString(),
                        transitionPayload(evaluation, transition.getTransitionStatus()));
            }

            if (!STATUS_ADULT_CONSENT_REQUIRED.equals(transition.getTransitionStatus())) {
                transition.setTransitionStatus(STATUS_ADULT_CONSENT_REQUIRED);
                transition.setLastEvaluatedAt(Instant.now());
                transition = transitionRepository.save(transition);

                writeAudit(actorId, "ADULT_CONSENT_REQUIRED", "Child", childId,
                        transitionPayload(evaluation, transition.getTransitionStatus()));
                writeOutbox(tenantId, "child.adult_consent_required", "Child", childId.toString(),
                        transitionPayload(evaluation, transition.getTransitionStatus()));
            }

            attemptNotification(actorId, child, transition, evaluation);
        }

        return MajorityTransitionResponse.fromEvaluation(evaluation, transition.getTransitionStatus());
    }

    @Transactional
    public AdultConsentLinkResponse linkAdultConsent(UUID tenantId, UUID childId, String receiptRef, String receiptSha256) {
        TenantContext context = TenantContextHolder.getContext();
        UUID actorId = context.getUserId();

        MajorityEvaluationResult evaluation = evaluateMajority(tenantId, childId, LocalDate.now());
        if (!evaluation.reached()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ADULT_CONSENT_NOT_REQUIRED");
        }

        Child child = childRepository.findByTenantIdAndChildId(tenantId, childId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Child not found"));

        ChildMajorityTransitionEntity transition = upsertTransition(tenantId, child, evaluation);
        if (!STATUS_ADULT_CONSENT_REQUIRED.equals(transition.getTransitionStatus())) {
            transition.setTransitionStatus(STATUS_ADULT_CONSENT_REQUIRED);
        }

        if (transition.getAdultConsentReceiptRef() != null) {
            if (transition.getAdultConsentReceiptRef().equals(receiptRef)) {
                return new AdultConsentLinkResponse(childId, receiptRef, transition.getAdultConsentRecordedAt());
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ADULT_CONSENT_ALREADY_LINKED");
        }

        transition.setAdultConsentReceiptRef(receiptRef);
        transition.setAdultConsentReceiptSha256(receiptSha256);
        transition.setAdultConsentRecordedAt(Instant.now());
        transitionRepository.save(transition);

        return new AdultConsentLinkResponse(childId, receiptRef, transition.getAdultConsentRecordedAt());
    }

    public List<UUID> loadTenantIdsForApprovedConsents() {
        return consentRepository.findAll().stream()
                .filter(consent -> ConsentStateMachine.STATUS_APPROVED.equals(consent.getStatus()))
                .map(consent -> consent.getTenantId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private ChildMajorityTransitionEntity upsertTransition(UUID tenantId, Child child, MajorityEvaluationResult evaluation) {
        LocalDate majorityDate = evaluation.majorityDate();

        Optional<ChildMajorityTransitionEntity> existing = transitionRepository
                .findByTenantIdAndChildIdAndMajorityDate(tenantId, child.getChildId(), majorityDate);

        ChildMajorityTransitionEntity transition = existing.orElseGet(ChildMajorityTransitionEntity::new);
        boolean created = existing.isEmpty();

        if (created) {
            transition.setTenantId(tenantId);
            transition.setChildId(child.getChildId());
            transition.setDob(child.getDateOfBirth());
            transition.setRegionCountryCode(evaluation.regionCountryCode());
            transition.setRegionStateCode(evaluation.regionStateCode());
            transition.setThresholdAgeYears(evaluation.thresholdAgeYears());
            transition.setMajorityDate(majorityDate);
            transition.setTransitionStatus(STATUS_TRANSITION_PENDING);
            transition.setCreatedAt(Instant.now());
        }

        transition.setLastEvaluatedAt(Instant.now());
        ChildMajorityTransitionEntity saved = transitionRepository.save(transition);

        if (created) {
            TenantContext context = TenantContextHolder.getContext();
            UUID actorId = context.getUserId();
            writeAudit(actorId, "CHILD_MAJORITY_THRESHOLD_REACHED", "Child", child.getChildId(),
                    transitionPayload(evaluation, saved.getTransitionStatus()));
            writeOutbox(tenantId, "child.majority_threshold_reached", "Child", child.getChildId().toString(),
                    transitionPayload(evaluation, saved.getTransitionStatus()));
        }

        return saved;
    }

    private void attemptNotification(UUID actorId, Child child, ChildMajorityTransitionEntity transition, MajorityEvaluationResult evaluation) {
        List<Guardian> guardians = guardianRepository.findByTenantIdAndChildId(child.getTenantId(), child.getChildId());
        List<String> emails = guardians.stream()
                .map(Guardian::getGuardianEmail)
                .filter(email -> email != null && !email.isBlank())
                .distinct()
                .toList();

        if (emails.isEmpty()) {
            transition.setLastNotificationStatus("NOT_DELIVERABLE");
            transition.setLastNotificationError("NO_EMAIL_AVAILABLE");
            transition.setLastNotificationAt(Instant.now());
            transitionRepository.save(transition);
            writeAudit(actorId, "MAJORITY_NOTIFICATION_NOT_DELIVERABLE", "Child", child.getChildId(),
                    Map.of("reason", "NO_EMAIL_AVAILABLE"));
            return;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("childId", child.getChildId().toString());
        variables.put("majorityDate", evaluation.majorityDate().toString());
        variables.put("thresholdAgeYears", evaluation.thresholdAgeYears());

        Map<String, Object> payload = notificationClient.buildEmailPayload(
                child.getTenantId().toString(),
                emails,
                "child-majority-reached",
                variables
        );

        try {
            notificationClient.sendEmail(payload);
            transition.setLastNotificationStatus("SENT");
            transition.setLastNotificationError(null);
        } catch (Exception e) {
            transition.setLastNotificationStatus("FAILED");
            transition.setLastNotificationError(e.getMessage());
            writeAudit(actorId, "MAJORITY_NOTIFICATION_FAILED", "Child", child.getChildId(),
                    Map.of("error", e.getMessage()));
        }

        transition.setLastNotificationAt(Instant.now());
        transitionRepository.save(transition);
    }

    private Map<String, Object> transitionPayload(MajorityEvaluationResult evaluation, String transitionStatus) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", evaluation.tenantId().toString());
        payload.put("childId", evaluation.childId().toString());
        payload.put("majorityDate", evaluation.majorityDate().toString());
        payload.put("thresholdAgeYears", evaluation.thresholdAgeYears());
        payload.put("regionCountryCode", evaluation.regionCountryCode());
        payload.put("regionStateCode", evaluation.regionStateCode());
        payload.put("transitionStatus", transitionStatus);
        payload.put("resolvedFrom", evaluation.resolvedFrom());
        return payload;
    }

    private String resolveCountry(Child child) {
        if (child.getRegionCountryCode() != null && !child.getRegionCountryCode().isBlank()) {
            return child.getRegionCountryCode();
        }
        if (child.getCountry() != null && !child.getCountry().isBlank()) {
            return child.getCountry();
        }
        return null;
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

    public record MajorityEvaluationResult(
            UUID tenantId,
            UUID childId,
            LocalDate dob,
            String regionCountryCode,
            String regionStateCode,
            short thresholdAgeYears,
            LocalDate majorityDate,
            boolean reached,
            String resolvedFrom,
            LocalDate evaluationDate,
            String error
    ) {
        public static MajorityEvaluationResult error(UUID childId, String error) {
            return new MajorityEvaluationResult(null, childId, null, null, null, (short) 0, null, false, null, null, error);
        }
    }

    public record MajorityTransitionResponse(
            UUID childId,
            LocalDate majorityDate,
            boolean reached,
            String transitionStatus,
            String resolvedFrom,
            String error
    ) {
        public static MajorityTransitionResponse fromEvaluation(MajorityEvaluationResult evaluation, String transitionStatus) {
            return new MajorityTransitionResponse(
                    evaluation.childId(),
                    evaluation.majorityDate(),
                    evaluation.reached(),
                    transitionStatus,
                    evaluation.resolvedFrom(),
                    evaluation.error()
            );
        }
    }

    public record AdultConsentLinkResponse(UUID childId, String consentReceiptRef, Instant recordedAt) {
    }
}
