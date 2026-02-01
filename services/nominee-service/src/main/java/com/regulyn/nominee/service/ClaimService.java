package com.regulyn.nominee.service;

import com.regulyn.common.enums.NomineeClaimStatus;
import com.regulyn.nominee.client.EvidenceClient;
import com.regulyn.nominee.dto.*;
import com.regulyn.nominee.entity.*;
import com.regulyn.nominee.repository.*;
import com.regulyn.events.publisher.OutboxEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final NomineeClaimWorkflowValidator workflowValidator;
    private final EvidenceClient evidenceClient;
    private final OutboxEventPublisher eventPublisher;

    public ClaimService(NomineeClaimRepository claimRepository,
                        ClaimDocumentRepository documentRepository,
                        ClaimStatusHistoryRepository historyRepository,
                        NomineeRepository nomineeRepository,
                        NomineeClaimWorkflowValidator workflowValidator,
                        EvidenceClient evidenceClient,
                        OutboxEventPublisher eventPublisher) {
        this.claimRepository = claimRepository;
        this.documentRepository = documentRepository;
        this.historyRepository = historyRepository;
        this.nomineeRepository = nomineeRepository;
        this.workflowValidator = workflowValidator;
        this.evidenceClient = evidenceClient;
        this.eventPublisher = eventPublisher;
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

        // Create evidence
        if (request.getIncludeEvidenceIds() != null && !request.getIncludeEvidenceIds().isEmpty()) {
            UUID bundleId = evidenceClient.createBundle("CLAIM_CLOSURE", request.getIncludeEvidenceIds());
            claim.setEvidenceId(bundleId);
            log.info("Created evidence bundle: bundleId={}", bundleId);
        }

        String oldStatus = claim.getStatus();
        claim.setStatus("CLOSED");
        claim.setClosedAt(Instant.now());

        claim = claimRepository.save(claim);

        recordStatusHistory(claimId, oldStatus, NomineeClaimStatus.CLOSED.name(), closedBy);

        eventPublisher.publish("claim.closed", claim.getId().toString(), claim);

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
