package com.regulyn.nominee.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.enums.NomineeClaimStatus;
import com.regulyn.events.model.ActorType;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.events.util.EventHasher;
import com.regulyn.events.util.EventJson;
import com.regulyn.nominee.audit.NomineeAuditWriter;
import com.regulyn.nominee.client.EvidenceClient;
import com.regulyn.nominee.dto.*;
import com.regulyn.nominee.entity.*;
import com.regulyn.nominee.repository.*;
import com.regulyn.events.publisher.OutboxEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    private final NomineeClaimRepository claimRepository;
    private final ClaimDocumentRepository documentRepository;
    private final ClaimStatusHistoryRepository historyRepository;
    private final NomineeRepository nomineeRepository;
    private final NomineeDocumentRepository nomineeDocumentRepository;
    private final NomineeClaimWorkflowValidator workflowValidator;
    private final EvidenceClient evidenceClient;
    private final OutboxEventPublisher eventPublisher;
    private final NomineeAuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final String serviceName;

    public ClaimService(NomineeClaimRepository claimRepository,
                        ClaimDocumentRepository documentRepository,
                        ClaimStatusHistoryRepository historyRepository,
                        NomineeRepository nomineeRepository,
                        NomineeDocumentRepository nomineeDocumentRepository,
                        NomineeClaimWorkflowValidator workflowValidator,
                        EvidenceClient evidenceClient,
                        OutboxEventPublisher eventPublisher,
                        NomineeAuditWriter auditWriter,
                        OutboxWriter outboxWriter,
                        ObjectMapper objectMapper,
                        @Value("${spring.application.name:nominee-service}") String serviceName) {
        this.claimRepository = claimRepository;
        this.documentRepository = documentRepository;
        this.historyRepository = historyRepository;
        this.nomineeRepository = nomineeRepository;
        this.nomineeDocumentRepository = nomineeDocumentRepository;
        this.workflowValidator = workflowValidator;
        this.evidenceClient = evidenceClient;
        this.eventPublisher = eventPublisher;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }

    @Transactional
    public ClaimResponse createClaim(UUID tenantId, CreateClaimRequest request) {
        log.info("Creating claim for nominee={}, type={}", request.getNomineeId(), request.getClaimType());

        // Validate nominee exists
        Nominee nominee = nomineeRepository.findById(request.getNomineeId())
            .orElseThrow(() -> new IllegalArgumentException("Nominee not found: " + request.getNomineeId()));

        NomineeClaim claim = new NomineeClaim();
        claim.setTenantId(tenantId);
        claim.setNomineeId(request.getNomineeId());
        claim.setClaimType(request.getClaimType().name());
        claim.setStatus("SUBMITTED");
        claim.setSubmittedAt(Instant.now());

        claim = claimRepository.save(claim);

        // Save documents
        if (request.getDocumentRefs() != null && !request.getDocumentRefs().isEmpty()) {
            for (CreateClaimRequest.DocumentRef docRef : request.getDocumentRefs()) {
                ClaimDocument doc = new ClaimDocument();
                doc.setClaimId(claim.getId());
                doc.setDocType(docRef.getDocType().name());
                doc.setStorageUrl(docRef.getDocRef());
                doc.setUploadedAt(Instant.now());
                documentRepository.save(doc);
            }
        }

        // Record status history
        recordStatusHistory(claim.getId(), null, "SUBMITTED", null);

        eventPublisher.publish("claim.created", claim.getId().toString(), claim);

        log.info("Claim created: id={}", claim.getId());
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse transitionClaim(UUID claimId, TransitionClaimRequest request, UUID userId) {
        log.info("Transitioning claim: id={}, to={}", claimId, request.getToStatus());

        NomineeClaim claim = claimRepository.findById(claimId)
            .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));

        String nextStatus = request.getToStatus().name();

        String oldStatus = claim.getStatus();
        claim.setStatus(nextStatus);
        claim = claimRepository.save(claim);

        recordStatusHistory(claimId, oldStatus, nextStatus, userId);

        eventPublisher.publish("claim.transitioned", claim.getId().toString(), 
            Map.of("from", oldStatus, "to", nextStatus));

        log.info("Claim transitioned: id={}, status={}", claim.getId(), nextStatus);
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse approveClaim(UUID claimId, ApproveClaimRequest request, UUID approvedBy) {
        log.info("Approving/Rejecting claim: id={}, decision={}", claimId, request.getDecision());

        NomineeClaim claim = claimRepository.findById(claimId)
            .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));

        claim.setApprovalDecision(request.getDecision().name());
        claim.setApprovedBy(approvedBy);
        claim.setApprovedAt(Instant.now());

        if (request.getDecision() == ApproveClaimRequest.Decision.APPROVE) {
            String oldStatus = claim.getStatus();
            claim.setStatus("APPROVED");
            recordStatusHistory(claimId, oldStatus, "APPROVED", approvedBy);
            eventPublisher.publish("claim.approved", claim.getId().toString(), claim);
        } else {
            String oldStatus = claim.getStatus();
            claim.setStatus("REJECTED");
            claim.setRejectionReason(request.getNotes());
            recordStatusHistory(claimId, oldStatus, "REJECTED", approvedBy);
            eventPublisher.publish("claim.rejected", claim.getId().toString(), claim);
        }

        claim = claimRepository.save(claim);

        log.info("Claim decision recorded: id={}, decision={}", claim.getId(), request.getDecision());
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse closeClaim(UUID claimId, CloseClaimRequest request, UUID closedBy) {
        log.info("Closing claim: id={}", claimId);

        NomineeClaim claim = claimRepository.findById(claimId)
            .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));

        UUID nomineeId = claim.getNomineeId();
        Nominee nominee = nomineeRepository.findById(nomineeId)
            .orElseThrow(() -> new IllegalArgumentException("Nominee not found: " + nomineeId));

        Map<String, Object> bundlePayload = buildBundlePayload(nominee, claim);
        UUID evidenceId = evidenceClient.createEvidence("NOMINEE_CLAIM_EVIDENCE", bundlePayload);
        List<UUID> bundleEvidenceIds = new ArrayList<>();
        bundleEvidenceIds.add(evidenceId);
        if (request.getIncludeEvidenceIds() != null && !request.getIncludeEvidenceIds().isEmpty()) {
            bundleEvidenceIds.addAll(request.getIncludeEvidenceIds());
        }

        boolean bundleCreated = false;
        if (claim.getEvidenceId() == null) {
            UUID bundleId = evidenceClient.createBundle("NOMINEE_CLAIM_BUNDLE", bundleEvidenceIds);
            claim.setEvidenceId(bundleId);
            bundleCreated = true;
        } else {
            evidenceClient.updateBundle(claim.getEvidenceId(), bundleEvidenceIds);
        }

        String oldStatus = claim.getStatus();
        claim.setStatus("CLOSED");
        claim.setClosedAt(Instant.now());

        claim = claimRepository.save(claim);

        recordStatusHistory(claimId, oldStatus, NomineeClaimStatus.CLOSED.name(), closedBy);

        eventPublisher.publish("claim.closed", claim.getId().toString(), claim);

        Map<String, Object> eventPayload = new HashMap<>(bundlePayload);
        eventPayload.put("bundleId", claim.getEvidenceId() != null ? claim.getEvidenceId().toString() : null);
        if (bundleCreated) {
            writeAuditAndOutbox(claim.getTenantId(), closedBy, "NOMINEE_EVIDENCE_BUNDLE_CREATED",
                    "nominee.evidence_bundle_created", claim.getId(), eventPayload, null);
        } else {
            writeAuditAndOutbox(claim.getTenantId(), closedBy, "NOMINEE_EVIDENCE_BUNDLE_UPDATED",
                    "nominee.evidence_bundle_updated", claim.getId(), eventPayload, null);
        }

        log.info("Claim closed: id={}", claim.getId());
        return toResponse(claim);
    }

    public ClaimResponse getClaim(UUID claimId) {
        NomineeClaim claim = claimRepository.findById(claimId)
            .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));
        return toResponse(claim);
    }

    public List<ClaimResponse> getClaimsByNominee(UUID nomineeId) {
        return claimRepository.findByNomineeId(nomineeId)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private void recordStatusHistory(UUID claimId, String fromStatus, String toStatus, UUID userId) {
        ClaimStatusHistory history = new ClaimStatusHistory();
        history.setClaimId(claimId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setTransitionedBy(userId);
        history.setTransitionedAt(Instant.now());
        historyRepository.save(history);
    }

    private Map<String, Object> buildBundlePayload(Nominee nominee, NomineeClaim claim) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", nominee.getTenantId().toString());
        payload.put("nomineeId", nominee.getId().toString());
        UUID claimId = claim.getId();
        payload.put("claimId", claimId.toString());

        List<NomineeDocument> docs = nomineeDocumentRepository.findByTenantIdAndNomineeId(nominee.getTenantId(), nominee.getId());
        List<Map<String, Object>> documentEntries = docs.stream()
                .filter(doc -> doc.getClaimId() == null || doc.getClaimId().equals(claimId))
                .map(doc -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("nomineeDocumentId", doc.getId().toString());
                    item.put("verificationStep", doc.getVerificationStep());
                    item.put("artifactRef", doc.getArtifactRef());
                    item.put("sha256Hash", doc.getSha256Hash());
                    item.put("filename", doc.getFilename());
                    item.put("contentType", doc.getContentType());
                    item.put("sizeBytes", doc.getSizeBytes());
                    if (doc.getUploadedAt() != null) {
                        item.put("uploadedAt", doc.getUploadedAt().toString());
                    }
                    if (doc.getClaimId() != null) {
                        item.put("claimId", doc.getClaimId().toString());
                    }
                    return item;
                })
                .collect(Collectors.toList());
        payload.put("documents", documentEntries);

        Map<String, Object> decision = new HashMap<>();
        decision.put("status", nominee.getStatus());
        decision.put("verificationMethod", nominee.getVerificationMethod());
        if (nominee.getVerifiedAt() != null) {
            decision.put("verifiedAt", nominee.getVerifiedAt().toString());
        }
        String decisionValue = "PENDING";
        if ("VERIFIED".equalsIgnoreCase(nominee.getStatus())) {
            decisionValue = "APPROVED";
        } else if ("REJECTED".equalsIgnoreCase(nominee.getStatus())) {
            decisionValue = "REJECTED";
        }
        decision.put("decision", decisionValue);
        decision.put("decidedBy", null);
        decision.put("decidedAt", nominee.getVerifiedAt() != null ? nominee.getVerifiedAt().toString() : null);
        decision.put("decisionNotes", null);
        decision.put("rejectionReason", null);
        payload.put("verificationDecision", decision);

        boolean exceptionUsed = docs.stream().anyMatch(doc -> "VERIFICATION_EXCEPTION".equals(doc.getVerificationStep()));
        payload.put("exceptionUsed", exceptionUsed);
        docs.stream().filter(doc -> "VERIFICATION_EXCEPTION".equals(doc.getVerificationStep())).findFirst().ifPresent(doc -> {
            Map<String, Object> exception = new HashMap<>();
            exception.put("artifactRef", doc.getArtifactRef());
            exception.put("sha256Hash", doc.getSha256Hash());
            payload.put("exception", exception);
        });

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
                .entityType("NOMINEE_CLAIM")
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
        envelope.setEntityType("NOMINEE_CLAIM");
        envelope.setEntityId(entityId.toString());
        envelope.setOccurredAt(Instant.now());
        envelope.setCorrelationId(entityId.toString());
        envelope.setIdempotencyKey(idempotencyKey);
        envelope.setPayload(EventJson.toJsonNode(payload));
        envelope.setPayloadHash(payloadHash);
        envelope.setSchemaVersion(1);

        outboxWriter.write(envelope);
    }

    private ClaimResponse toResponse(NomineeClaim claim) {
        ClaimResponse response = new ClaimResponse();
        response.setClaimId(claim.getId());
        response.setNomineeId(claim.getNomineeId());
        response.setDataPrincipalId(claim.getTenantId()); // Using tenant as principal
        response.setClaimType(claim.getClaimType());
        response.setStatus(claim.getStatus());
        response.setApprovedBy(claim.getApprovedBy());
        response.setApprovedAt(claim.getApprovedAt());
        response.setClosedAt(claim.getClosedAt());
        response.setEvidenceBundleId(claim.getEvidenceId());

        // Load documents
        List<ClaimDocument> documents = documentRepository.findByClaimId(claim.getId());
        List<ClaimResponse.DocumentRefResponse> docResponses = documents.stream()
            .map(doc -> {
                ClaimResponse.DocumentRefResponse docResp = new ClaimResponse.DocumentRefResponse();
                docResp.setDocId(doc.getId());
                docResp.setDocType(doc.getDocType());
                docResp.setDocRef(doc.getStorageUrl());
                docResp.setCreatedAt(doc.getUploadedAt());
                return docResp;
            })
            .collect(Collectors.toList());
        response.setDocumentRefs(docResponses);

        return response;
    }
}
