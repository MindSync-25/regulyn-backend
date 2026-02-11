package com.regulyn.nominee.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.enums.NomineeStatus;
import com.regulyn.events.model.ActorType;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.events.util.EventHasher;
import com.regulyn.events.util.EventJson;
import com.regulyn.nominee.audit.NomineeAuditWriter;
import com.regulyn.nominee.dto.RegisterNomineeRequest;
import com.regulyn.nominee.dto.NomineeResponse;
import com.regulyn.nominee.dto.VerifyNomineeRequest;
import com.regulyn.nominee.dto.VerifyNomineeRejectRequest;
import com.regulyn.nominee.entity.Nominee;
import com.regulyn.nominee.entity.NomineeDocument;
import com.regulyn.nominee.exception.VerificationGateException;
import com.regulyn.nominee.repository.NomineeDocumentRepository;
import com.regulyn.nominee.repository.NomineeRepository;
import com.regulyn.events.publisher.OutboxEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NomineeService {

    private static final Logger log = LoggerFactory.getLogger(NomineeService.class);

    private final NomineeRepository nomineeRepository;
    private final NomineeWorkflowValidator workflowValidator;
    private final OutboxEventPublisher eventPublisher;
    private final NomineeVerificationGatingService gatingService;
    private final NomineeDocumentRepository nomineeDocumentRepository;
    private final NomineeAuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final String serviceName;

    public NomineeService(NomineeRepository nomineeRepository,
                          NomineeWorkflowValidator workflowValidator,
                          OutboxEventPublisher eventPublisher,
                          NomineeVerificationGatingService gatingService,
                          NomineeDocumentRepository nomineeDocumentRepository,
                          NomineeAuditWriter auditWriter,
                          OutboxWriter outboxWriter,
                          ObjectMapper objectMapper,
                          @Value("${spring.application.name:nominee-service}") String serviceName) {
        this.nomineeRepository = nomineeRepository;
        this.workflowValidator = workflowValidator;
        this.eventPublisher = eventPublisher;
        this.gatingService = gatingService;
        this.nomineeDocumentRepository = nomineeDocumentRepository;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }

    @Transactional
    public NomineeResponse registerNominee(UUID tenantId, UUID dataPrincipalId, RegisterNomineeRequest request) {
        log.info("Registering nominee for tenant={}, principal={}", tenantId, dataPrincipalId);

        Nominee nominee = new Nominee();
        nominee.setTenantId(tenantId);
        nominee.setDataPrincipalId(dataPrincipalId);
        nominee.setNomineeName(request.getNomineeName());
        // nomineeContact stored as email for now
        if (request.getNomineeEmail() != null) {
            nominee.setNomineeContact(request.getNomineeEmail());
        }
        nominee.setRelationship(request.getRelationship().name());
        nominee.setScope(request.getScope().name());
        nominee.setStatus("PENDING");
        nominee.setRegisteredAt(Instant.now());

        nominee = nomineeRepository.save(nominee);

        eventPublisher.publish("nominee.registered", nominee.getId().toString(), nominee);

        log.info("Nominee registered: id={}", nominee.getId());
        return toResponse(nominee);
    }

    @Transactional
    public NomineeResponse verifyNominee(UUID nomineeId, UUID verifiedBy, VerifyNomineeRequest request) {
        log.info("Verifying nominee: id={}, method={}", nomineeId, request.getMethod());

        Nominee nominee = nomineeRepository.findById(nomineeId)
            .orElseThrow(() -> new IllegalArgumentException("Nominee not found: " + nomineeId));

        NomineeVerificationGatingService.GatingResult gatingResult;
        boolean requiresDocuments = request.getMethod() == VerifyNomineeRequest.VerificationMethod.DOC_CHECK
                || request.getMethod() == VerifyNomineeRequest.VerificationMethod.MANUAL;
        if (requiresDocuments) {
            gatingResult = gatingService.evaluate(nominee.getTenantId(), nomineeId);
            if (!gatingResult.ok()) {
                throw new VerificationGateException(gatingResult.missingSteps(), true);
            }
        } else {
            gatingResult = new NomineeVerificationGatingService.GatingResult(true, List.of(), false);
        }

        NomineeDocument exceptionDoc = null;
        if (gatingResult.exceptionPresent()) {
            exceptionDoc = nomineeDocumentRepository
                    .findByTenantIdAndNomineeIdAndVerificationStep(nominee.getTenantId(), nomineeId,
                            NomineeVerificationGatingService.STEP_EXCEPTION)
                    .stream()
                    .findFirst()
                    .orElse(null);
        }

        Instant decidedAt = Instant.now();
        Map<String, Object> payload = buildVerificationPayload(nominee, request.getMethod() != null ? request.getMethod().name() : null,
            "APPROVED", gatingResult.exceptionPresent(), exceptionDoc,
            verifiedBy, decidedAt, request.getNotes(), null);
        writeAuditAndOutbox(nominee.getTenantId(), verifiedBy, "NOMINEE_VERIFICATION_SUBMITTED", "nominee.verification_submitted", nominee.getId(), payload, null);

        // Skip workflow validation for now
        nominee.setStatus("VERIFIED");
        nominee.setVerificationMethod(request.getMethod().name());
        nominee.setVerifiedAt(decidedAt);

        nominee = nomineeRepository.save(nominee);

        writeAuditAndOutbox(nominee.getTenantId(), verifiedBy, "NOMINEE_VERIFICATION_APPROVED", "nominee.verification_approved", nominee.getId(), payload, null);

        eventPublisher.publish("nominee.verified", nominee.getId().toString(), nominee);

        log.info("Nominee verified: id={}", nominee.getId());
        return toResponse(nominee);
    }

    @Transactional
    public NomineeResponse rejectNominee(UUID nomineeId, UUID rejectedBy, VerifyNomineeRejectRequest request) {
        log.info("Rejecting nominee: id={}, by={}", nomineeId, rejectedBy);

        Nominee nominee = nomineeRepository.findById(nomineeId)
                .orElseThrow(() -> new IllegalArgumentException("Nominee not found: " + nomineeId));

        String decisionNotes = request.getNotes();
        String rejectionReason = request.getRejectionReason();
        Instant decidedAt = Instant.now();

        Map<String, Object> payload = buildVerificationPayload(
            nominee,
            request.getMethod() != null ? request.getMethod().name() : null,
            "REJECTED",
                false,
                null,
                rejectedBy,
                decidedAt,
                decisionNotes,
                rejectionReason
        );

        writeAuditAndOutbox(nominee.getTenantId(), rejectedBy, "NOMINEE_VERIFICATION_REJECTED",
                "nominee.verification_rejected", nominee.getId(), payload, null);

        nominee.setStatus("REJECTED");
        if (request.getMethod() != null) {
            nominee.setVerificationMethod(request.getMethod().name());
        }
        nominee.setVerifiedAt(null);

        nominee = nomineeRepository.save(nominee);

        log.info("Nominee rejected: id={}", nominee.getId());
        return toResponse(nominee);
    }

    @Transactional
    public void disableNominee(UUID nomineeId, UUID disabledBy) {
        log.info("Disabling nominee: id={}, by={}", nomineeId, disabledBy);

        Nominee nominee = nomineeRepository.findById(nomineeId)
            .orElseThrow(() -> new IllegalArgumentException("Nominee not found: " + nomineeId));

        // Skip workflow validation for now
        nominee.setStatus("DISABLED");
        nominee.setDisabledAt(Instant.now());
        nominee.setDisabledBy(disabledBy);

        nomineeRepository.save(nominee);

        eventPublisher.publish("nominee.disabled", nominee.getId().toString(), nominee);

        log.info("Nominee disabled: id={}", nominee.getId());
    }

    public NomineeResponse getNominee(UUID nomineeId) {
        Nominee nominee = nomineeRepository.findById(nomineeId)
            .orElseThrow(() -> new IllegalArgumentException("Nominee not found: " + nomineeId));
        return toResponse(nominee);
    }

    public List<NomineeResponse> getNomineesByPrincipal(UUID tenantId, UUID dataPrincipalId) {
        return nomineeRepository.findByTenantIdAndDataPrincipalId(tenantId, dataPrincipalId)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private NomineeResponse toResponse(Nominee nominee) {
        NomineeResponse response = new NomineeResponse();
        response.setNomineeId(nominee.getId());
        response.setDataPrincipalId(nominee.getDataPrincipalId());
        response.setNomineeName(nominee.getNomineeName());
        response.setNomineeEmail(nominee.getNomineeContact()); // Contact stored as email
        response.setRelationship(nominee.getRelationship());
        response.setScope(nominee.getScope());
        response.setStatus(nominee.getStatus());
        response.setVerifiedAt(nominee.getVerifiedAt());
        response.setVerifiedBy(null);
        return response;
    }

    private Map<String, Object> buildVerificationPayload(Nominee nominee,
                                                         String verificationMethod,
                                                         String decision,
                                                         boolean exceptionUsed,
                                                         NomineeDocument exceptionDoc,
                                                         UUID decidedBy,
                                                         Instant decidedAt,
                                                         String decisionNotes,
                                                         String rejectionReason) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("tenantId", nominee.getTenantId().toString());
        payload.put("nomineeId", nominee.getId().toString());
        payload.put("decision", decision);
        payload.put("verificationMethod", verificationMethod);
        payload.put("decidedBy", decidedBy != null ? decidedBy.toString() : null);
        payload.put("decidedAt", decidedAt != null ? decidedAt.toString() : null);
        payload.put("decisionNotes", decisionNotes);
        payload.put("rejectionReason", rejectionReason);
        payload.put("exceptionUsed", exceptionUsed);
        if (exceptionDoc != null) {
            payload.put("exceptionArtifactRef", exceptionDoc.getArtifactRef());
            payload.put("exceptionSha256", exceptionDoc.getSha256Hash());
        }
        payload.put("timestamp", Instant.now().toString());
        return payload;
    }

    private void writeAuditAndOutbox(
            UUID tenantId,
            UUID userId,
            String action,
            String eventType,
            UUID entityId,
            Map<String, Object> payload,
            String idempotencyKey) {
        JsonNode metadata = objectMapper.valueToTree(payload);
        String payloadHash = EventHasher.sha256(EventJson.toCanonicalJson(payload));

        AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(userId)
                .actorType(userId != null ? AuditEvent.ActorType.USER : AuditEvent.ActorType.SYSTEM)
                .service(serviceName)
                .action(action)
                .entityType("NOMINEE")
                .entityId(entityId.toString())
                .payloadHash(payloadHash)
                .metadata(metadata)
                .build();

        auditWriter.writeOrThrow(auditEvent);

        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setEventType(eventType);
        envelope.setTenantId(tenantId);
        envelope.setActorId(userId);
        envelope.setActorType(userId != null ? ActorType.USER : ActorType.SYSTEM);
        envelope.setSourceService(serviceName);
        envelope.setEntityType("NOMINEE");
        envelope.setEntityId(entityId.toString());
        envelope.setOccurredAt(Instant.now());
        envelope.setCorrelationId(entityId.toString());
        envelope.setIdempotencyKey(idempotencyKey);
        envelope.setPayload(EventJson.toJsonNode(payload));
        envelope.setPayloadHash(payloadHash);
        envelope.setSchemaVersion(1);

        outboxWriter.write(envelope);
    }
}
