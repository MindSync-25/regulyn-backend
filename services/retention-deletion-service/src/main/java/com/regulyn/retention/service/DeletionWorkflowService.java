package com.regulyn.retention.service;

import com.regulyn.retention.cascade.DeletionCascadeAuditActions;
import com.regulyn.retention.cascade.DeletionCascadeEventTypes;
import com.regulyn.retention.entity.*;
import com.regulyn.retention.enums.DeletionSystemExecutionStatus;
import com.regulyn.retention.integration.EvidenceServiceClient;
import com.regulyn.retention.integration.EvidenceServiceClient.EvidenceServiceUnavailableException;
import com.regulyn.retention.model.*;
import com.regulyn.retention.repository.*;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.evidence.store.LocalFileSystemArtifactStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeletionWorkflowService {

    private final RetentionRuleRepository retentionRuleRepository;
    private final DeletionRequestRepository deletionRequestRepository;
    private final DeletionProofRepository deletionProofRepository;
    private final DeletionStatusHistoryRepository deletionStatusHistoryRepository;
    private final RetentionCandidateRepository retentionCandidateRepository;
    private final DeletionExecutionPlanRepository planRepository;
    private final DeletionSystemExecutionRepository executionRepository;
    private final DeletionStateMachine stateMachine;
    private final EvidenceServiceClient evidenceServiceClient;
    private final LocalFileSystemArtifactStore artifactStore;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;

    public DeletionWorkflowService(
            RetentionRuleRepository retentionRuleRepository,
            DeletionRequestRepository deletionRequestRepository,
            DeletionProofRepository deletionProofRepository,
            DeletionStatusHistoryRepository deletionStatusHistoryRepository,
            RetentionCandidateRepository retentionCandidateRepository,
            DeletionExecutionPlanRepository planRepository,
            DeletionSystemExecutionRepository executionRepository,
            DeletionStateMachine stateMachine,
            EvidenceServiceClient evidenceServiceClient,
            LocalFileSystemArtifactStore artifactStore,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter) {
        this.retentionRuleRepository = retentionRuleRepository;
        this.deletionRequestRepository = deletionRequestRepository;
        this.deletionProofRepository = deletionProofRepository;
        this.deletionStatusHistoryRepository = deletionStatusHistoryRepository;
        this.retentionCandidateRepository = retentionCandidateRepository;
        this.planRepository = planRepository;
        this.executionRepository = executionRepository;
        this.stateMachine = stateMachine;
        this.evidenceServiceClient = evidenceServiceClient;
        this.artifactStore = artifactStore;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
    }

    // ============ Retention Rules ============

    @Transactional
    public CreateRetentionRuleResponse createRetentionRule(
            UUID tenantId,
            UUID userId,
            CreateRetentionRuleRequest request) {

        // Check for duplicate rule name
        retentionRuleRepository.findByTenantIdAndRuleName(tenantId, request.getRuleName())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Retention rule with name '" + request.getRuleName() + "' already exists");
                });

        RetentionRule rule = new RetentionRule();
        rule.setTenantId(tenantId);
        rule.setRuleName(request.getRuleName());
        rule.setSubjectType(request.getSubjectType());
        rule.setEntityType(request.getEntityType());
        rule.setRetentionDays(request.getRetentionDays());
        rule.setAction(request.getAction());
        rule.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        rule.setMetadata(request.getMetadata() != null ? toJson(request.getMetadata()) : "{}");
        rule.setCreatedBy(userId);

        rule = retentionRuleRepository.save(rule);

        // Audit
        writeAudit(tenantId, userId, "retention.rule_created", "RetentionRule", rule.getRuleId(), "Created retention rule: " + request.getRuleName());

        // Outbox event
        writeOutboxEvent(tenantId, "retention.rule_created", Map.of(
                "ruleId", rule.getRuleId(),
                "ruleName", request.getRuleName(),
                "subjectType", request.getSubjectType(),
                "entityType", request.getEntityType(),
                "retentionDays", request.getRetentionDays()
        ));

        CreateRetentionRuleResponse response = new CreateRetentionRuleResponse();
        response.setRuleId(rule.getRuleId());
        response.setEnabled(rule.getEnabled());
        return response;
    }

    @Transactional(readOnly = true)
    public List<CreateRetentionRuleResponse> getRetentionRules(UUID tenantId) {
        List<RetentionRule> rules = retentionRuleRepository.findByTenantIdAndEnabled(tenantId, true);
        return rules.stream()
                .map(rule -> {
                    CreateRetentionRuleResponse response = new CreateRetentionRuleResponse();
                    response.setRuleId(rule.getRuleId());
                    response.setEnabled(rule.getEnabled());
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void disableRetentionRule(UUID tenantId, UUID userId, UUID ruleId) {
        RetentionRule rule = retentionRuleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Retention rule not found"));

        if (!rule.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Retention rule does not belong to tenant");
        }

        rule.setEnabled(false);
        retentionRuleRepository.save(rule);

        // Audit
        writeAudit(tenantId, userId, "retention.rule_disabled", "RetentionRule", ruleId, "Disabled retention rule");

        // Outbox event
        writeOutboxEvent(tenantId, "retention.rule_disabled", Map.of("ruleId", ruleId));
    }

    // Helper methods will be added in next sections...

    // ============ Deletion Workflow ============

    @Transactional
    public CreateDeletionResponse createDeletion(
            UUID tenantId,
            UUID userId,
            CreateDeletionRequest request,
            String idempotencyKey) {

        // Check idempotency
        if (idempotencyKey != null) {
            Optional<DeletionRequest> existing = deletionRequestRepository
                    .findByTenantIdAndSubjectIdAndEntityTypeAndIdempotencyKey(
                            tenantId, request.getSubjectId(), request.getEntityType(), idempotencyKey);
            if (existing.isPresent()) {
                DeletionRequest existingRequest = existing.get();
                CreateDeletionResponse response = new CreateDeletionResponse();
                response.setDeletionId(existingRequest.getDeletionId());
                response.setStatus(existingRequest.getStatus());
                response.setDueAt(existingRequest.getDueAt());
                return response;
            }
        }

        DeletionRequest deletion = new DeletionRequest();
        deletion.setTenantId(tenantId);
        deletion.setSubjectId(request.getSubjectId());
        deletion.setSubjectType(request.getSubjectType());
        deletion.setEntityType(request.getEntityType());
        deletion.setSource(request.getSource());
        deletion.setReason(request.getReason());
        deletion.setStatus("REQUESTED");
        deletion.setRequiresApproval(request.getRequiresApproval() != null ? request.getRequiresApproval() : true);
        deletion.setProofRequired(request.getProofRequired() != null ? request.getProofRequired() : true);
        deletion.setIdempotencyKey(idempotencyKey);
        deletion.setMetadata(request.getMetadata() != null ? toJson(request.getMetadata()) : "{}");

        // Calculate due date
        int dueInDays = request.getDueInDays() != null ? request.getDueInDays() : 30;
        deletion.setDueAt(Instant.now().plusSeconds(dueInDays * 24L * 60 * 60));

        deletion = deletionRequestRepository.save(deletion);

        // Record initial status
        recordStatusChange(deletion.getDeletionId(), tenantId, null, "REQUESTED", userId, "Deletion request created");

        // Audit
        writeAudit(tenantId, userId, "deletion.created", "DeletionRequest", deletion.getDeletionId(), 
                "Created deletion request for " + request.getSubjectType() + " " + request.getSubjectId());

        // Outbox event
        writeOutboxEvent(tenantId, "deletion.created", Map.of(
                "deletionId", deletion.getDeletionId(),
                "subjectId", request.getSubjectId(),
                "entityType", request.getEntityType(),
                "source", request.getSource()
        ));

        CreateDeletionResponse response = new CreateDeletionResponse();
        response.setDeletionId(deletion.getDeletionId());
        response.setStatus(deletion.getStatus());
        response.setDueAt(deletion.getDueAt());
        return response;
    }

    @Transactional
    public AssignDeletionResponse assignDeletion(UUID tenantId, UUID userId, UUID deletionId, AssignDeletionRequest request) {
        DeletionRequest deletion = getDeletionOrThrow(tenantId, deletionId);

        // Auto-transition to IN_REVIEW when assigning
        String oldStatus = deletion.getStatus();
        if ("REQUESTED".equals(oldStatus)) {
            deletion.setStatus("IN_REVIEW");
            recordStatusChange(deletionId, tenantId, oldStatus, "IN_REVIEW", userId, "Moved to review upon assignment");
        }

        deletion.setAssignedTo(request.getAssignedTo());
        deletionRequestRepository.save(deletion);

        // Audit
        writeAudit(tenantId, userId, "deletion.assigned", "DeletionRequest", deletionId,
                "Assigned deletion to user " + request.getAssignedTo());

        // Outbox event
        writeOutboxEvent(tenantId, "deletion.assigned", Map.of(
                "deletionId", deletionId,
                "assignedTo", request.getAssignedTo()
        ));

        AssignDeletionResponse response = new AssignDeletionResponse();
        response.setDeletionId(deletionId);
        response.setStatus(deletion.getStatus());
        response.setAssignedTo(request.getAssignedTo());
        return response;
    }

    @Transactional
    public ApproveDeletionResponse approveDeletion(UUID tenantId, UUID userId, UUID deletionId, ApproveDeletionRequest request) {
        DeletionRequest deletion = getDeletionOrThrow(tenantId, deletionId);

        if (!stateMachine.canApprove(deletion.getStatus())) {
            throw new IllegalStateException("Cannot approve deletion in status: " + deletion.getStatus());
        }

        String newStatus = "APPROVE".equals(request.getDecision()) ? "APPROVED" : "REJECTED";

        if (!stateMachine.isValidTransition(deletion.getStatus(), newStatus)) {
            throw new IllegalStateException("Invalid transition from " + deletion.getStatus() + " to " + newStatus);
        }

        String oldStatus = deletion.getStatus();
        deletion.setStatus(newStatus);

        if ("APPROVED".equals(newStatus)) {
            deletion.setApprovedBy(userId);
            deletion.setApprovedAt(Instant.now());
        }

        deletionRequestRepository.save(deletion);

        // Record status change
        recordStatusChange(deletionId, tenantId, oldStatus, newStatus, userId, request.getReason());

        // Audit
        writeAudit(tenantId, userId, "deletion." + request.getDecision().toLowerCase(), "DeletionRequest", deletionId,
                request.getDecision() + " deletion request");

        // Outbox event
        writeOutboxEvent(tenantId, "deletion." + request.getDecision().toLowerCase(), Map.of(
                "deletionId", deletionId,
                "decision", request.getDecision(),
                "reason", request.getReason() != null ? request.getReason() : ""
        ));

        ApproveDeletionResponse response = new ApproveDeletionResponse();
        response.setDeletionId(deletionId);
        response.setStatus(newStatus);
        return response;
    }

    @Transactional
    public TransitionDeletionResponse transitionStatus(UUID tenantId, UUID userId, UUID deletionId, TransitionDeletionRequest request) {
        DeletionRequest deletion = getDeletionOrThrow(tenantId, deletionId);

        String toStatus = request.getToStatus();

        // Validate transition
        if (!stateMachine.isValidTransition(deletion.getStatus(), toStatus)) {
            throw new IllegalStateException("Invalid transition from " + deletion.getStatus() + " to " + toStatus);
        }

        // Check approval requirement for IN_PROGRESS
        if (!stateMachine.requiresApprovalCheck(deletion.getStatus(), toStatus, deletion.getRequiresApproval())) {
            throw new IllegalStateException("Deletion requires approval before moving to IN_PROGRESS");
        }

        if ("COMPLETED".equalsIgnoreCase(toStatus)) {
            ensureCascadeProofComplete(tenantId, userId, deletionId);
        }

        String oldStatus = deletion.getStatus();
        deletion.setStatus(toStatus);
        deletionRequestRepository.save(deletion);

        // Record status change
        recordStatusChange(deletionId, tenantId, oldStatus, toStatus, userId, request.getReason());

        // Audit
        writeAudit(tenantId, userId, "deletion.status_changed", "DeletionRequest", deletionId,
                "Status changed from " + oldStatus + " to " + toStatus);

        // Outbox event
        writeOutboxEvent(tenantId, "deletion.status_changed", Map.of(
                "deletionId", deletionId,
                "fromStatus", oldStatus,
                "toStatus", toStatus
        ));

        if ("COMPLETED".equalsIgnoreCase(toStatus)) {
            Map<String, Object> payload = buildCascadeCompletionPayload(tenantId, deletionId, toStatus);
            writeAudit(tenantId, userId, DeletionCascadeAuditActions.DELETION_COMPLETED,
                "DeletionRequest", deletionId, payload);
            writeOutboxEvent(tenantId, DeletionCascadeEventTypes.DELETION_COMPLETED, payload);
        }

        TransitionDeletionResponse response = new TransitionDeletionResponse();
        response.setDeletionId(deletionId);
        response.setStatus(toStatus);
        return response;
    }

    @Transactional
    public UploadProofResponse uploadProof(UUID tenantId, UUID userId, UUID deletionId, MultipartFile file) throws IOException {
        DeletionRequest deletion = getDeletionOrThrow(tenantId, deletionId);

        // Read file bytes
        byte[] fileBytes = file.getBytes();
        
        // Store artifact
        String artifactRef = artifactStore.store(tenantId, deletionId, file.getOriginalFilename(), fileBytes, file.getContentType());
        String artifactHash = computeHash(fileBytes);

        // Save proof
        DeletionProof proof = new DeletionProof();
        proof.setTenantId(tenantId);
        proof.setDeletionId(deletionId);
        proof.setFilename(file.getOriginalFilename());
        proof.setArtifactRef(artifactRef);
        proof.setArtifactHash(artifactHash);
        proof.setSizeBytes(file.getSize());
        proof.setUploadedBy(userId);

        proof = deletionProofRepository.save(proof);

        // Audit
        writeAudit(tenantId, userId, "deletion.proof_uploaded", "DeletionProof", proof.getProofId(),
                "Uploaded proof for deletion " + deletionId);

        // Outbox event
        writeOutboxEvent(tenantId, "deletion.proof_uploaded", Map.of(
                "deletionId", deletionId,
                "proofId", proof.getProofId(),
                "filename", file.getOriginalFilename()
        ));

        UploadProofResponse response = new UploadProofResponse();
        response.setProofId(proof.getProofId());
        response.setArtifactHash(artifactHash);
        response.setArtifactRef(artifactRef);
        return response;
    }

    @Transactional
    public CloseDeletionResponse closeDeletion(UUID tenantId, UUID userId, UUID deletionId, CloseDeletionRequest request) {
        DeletionRequest deletion = getDeletionOrThrow(tenantId, deletionId);

        if ("CLOSED".equalsIgnoreCase(deletion.getStatus()) && deletion.getEvidenceBundleId() != null) {
            CloseDeletionResponse response = new CloseDeletionResponse();
            response.setDeletionId(deletionId);
            response.setStatus("CLOSED");
            response.setEvidenceBundleId(deletion.getEvidenceBundleId());
            return response;
        }

        if (!stateMachine.canClose(deletion.getStatus())) {
            throw new IllegalStateException("Cannot close deletion in status: " + deletion.getStatus());
        }

        // Check proof requirement
        if (deletion.getProofRequired() && deletionProofRepository.countByDeletionId(deletionId) == 0) {
            throw new IllegalStateException("Proof required before closing deletion");
        }

        // Create evidence bundle
        UUID bundleId;
        try {
            List<DeletionProof> proofs = deletionProofRepository.findByDeletionId(deletionId);
            List<String> artifactHashes = proofs.stream()
                    .map(DeletionProof::getArtifactHash)
                    .collect(Collectors.toList());

            DeletionExecutionPlan plan = latestPlan(tenantId, deletionId).orElse(null);
            List<Map<String, Object>> executionArtifacts = buildExecutionArtifacts(tenantId, deletionId, plan);

            Map<String, Object> metadata = new HashMap<>();
            if (plan != null) {
            metadata.put("planId", plan.getPlanId());
            metadata.put("planVersion", plan.getPlanVersion());
            metadata.put("planHash", plan.getPlanHashSha256());
            }
            metadata.put("executionArtifacts", executionArtifacts);

            var evidenceResp = evidenceServiceClient.createEvidence(
                tenantId, userId, "DELETION", deletionId, deletion.getSubjectId(),
                deletion.getEntityType(), deletion.getStatus(), artifactHashes, metadata);

            var bundleResp = evidenceServiceClient.createBundle(
                tenantId, userId, "DELETION", "DELETION_REQUEST", deletionId,
                List.of(evidenceResp.getEvidenceId()), metadata);

            bundleId = bundleResp.getBundleId();

        } catch (EvidenceServiceUnavailableException e) {
            throw new EvidenceServiceUnavailableException("Evidence service unavailable - cannot close deletion", e);
        }

        // Update deletion
        deletion.setStatus("CLOSED");
        deletion.setClosedAt(Instant.now());
        deletion.setEvidenceBundleId(bundleId);
        deletionRequestRepository.save(deletion);

        // Record status change
        recordStatusChange(deletionId, tenantId, "COMPLETED", "CLOSED", userId, request.getClosureNotes());

        // Audit
        writeAudit(tenantId, userId, "deletion.closed", "DeletionRequest", deletionId, "Closed deletion request");

        // Outbox event
        writeOutboxEvent(tenantId, "deletion.closed", Map.of(
                "deletionId", deletionId,
                "evidenceBundleId", bundleId
        ));

        CloseDeletionResponse response = new CloseDeletionResponse();
        response.setDeletionId(deletionId);
        response.setStatus("CLOSED");
        response.setEvidenceBundleId(bundleId);
        return response;
    }

    @Transactional(readOnly = true)
    public DeletionDetailResponse getDeletion(UUID tenantId, UUID deletionId) {
        DeletionRequest deletion = getDeletionOrThrow(tenantId, deletionId);
        return mapToDetailResponse(deletion);
    }

    @Transactional(readOnly = true)
    public Page<DeletionDetailResponse> searchDeletions(UUID tenantId, String status, Pageable pageable) {
        Page<DeletionRequest> page;
        if (status != null) {
            page = deletionRequestRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else {
            page = deletionRequestRepository.findByTenantId(tenantId, pageable);
        }
        return page.map(this::mapToDetailResponse);
    }

    // ============ Retention Candidates ============

    @Transactional
    public CreateRetentionCandidateResponse createRetentionCandidate(UUID tenantId, CreateRetentionCandidateRequest request) {
        // Upsert candidate
        Optional<RetentionCandidate> existing = retentionCandidateRepository
                .findByTenantIdAndSubjectIdAndEntityType(tenantId, request.getSubjectId(), request.getEntityType());

        RetentionCandidate candidate;
        if (existing.isPresent()) {
            candidate = existing.get();
            candidate.setLastSeenAt(request.getLastSeenAt() != null ? request.getLastSeenAt() : Instant.now());
            if (request.getMetadata() != null) {
                candidate.setMetadata(toJson(request.getMetadata()));
            }
        } else {
            candidate = new RetentionCandidate();
            candidate.setTenantId(tenantId);
            candidate.setSubjectId(request.getSubjectId());
            candidate.setSubjectType(request.getSubjectType());
            candidate.setEntityType(request.getEntityType());
            candidate.setLastSeenAt(request.getLastSeenAt() != null ? request.getLastSeenAt() : Instant.now());
            candidate.setMetadata(request.getMetadata() != null ? toJson(request.getMetadata()) : "{}");
        }

        candidate = retentionCandidateRepository.save(candidate);

        CreateRetentionCandidateResponse response = new CreateRetentionCandidateResponse();
        response.setCandidateId(candidate.getCandidateId());
        return response;
    }

    // ============ Helper Methods ============

    private DeletionRequest getDeletionOrThrow(UUID tenantId, UUID deletionId) {
        return deletionRequestRepository.findByDeletionIdAndTenantId(deletionId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Deletion request not found"));
    }

    private void recordStatusChange(UUID deletionId, UUID tenantId, String fromStatus, String toStatus, UUID changedBy, String reason) {
        DeletionStatusHistory history = new DeletionStatusHistory();
        history.setDeletionId(deletionId);
        history.setTenantId(tenantId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangedBy(changedBy);
        history.setReason(reason);
        deletionStatusHistoryRepository.save(history);
    }

    private DeletionDetailResponse mapToDetailResponse(DeletionRequest deletion) {
        DeletionDetailResponse response = new DeletionDetailResponse();
        response.setDeletionId(deletion.getDeletionId());
        response.setSubjectId(deletion.getSubjectId());
        response.setSubjectType(deletion.getSubjectType());
        response.setEntityType(deletion.getEntityType());
        response.setStatus(deletion.getStatus());
        response.setSource(deletion.getSource());
        response.setReason(deletion.getReason());
        response.setRequiresApproval(deletion.getRequiresApproval());
        response.setProofRequired(deletion.getProofRequired());
        response.setAssignedTo(deletion.getAssignedTo());
        response.setApprovedBy(deletion.getApprovedBy());
        response.setApprovedAt(deletion.getApprovedAt());
        response.setCreatedAt(deletion.getCreatedAt());
        response.setUpdatedAt(deletion.getUpdatedAt());
        response.setDueAt(deletion.getDueAt());
        response.setClosedAt(deletion.getClosedAt());
        response.setEvidenceBundleId(deletion.getEvidenceBundleId());
        response.setMetadata(fromJson(deletion.getMetadata()));
        return response;
    }

    private void writeAudit(UUID tenantId, UUID userId, String action, String entityType, UUID entityId, String description) {
        try {
            String payloadHash = computeHashString(description);
            AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(userId)
                .actorType(AuditEvent.ActorType.USER)
                .action(action)
                .entityType(entityType)
                .entityId(entityId.toString())
                .payloadHash(payloadHash)
                .build();
            
            auditWriter.write(auditEvent);
        } catch (Exception e) {
            // Log but don't fail the operation
            System.err.println("Failed to write audit event: " + e.getMessage());
        }
    }

    private void writeAudit(UUID tenantId, UUID userId, String action, String entityType, UUID entityId, Map<String, Object> payload) {
        try {
            String payloadJson = toJson(payload);
            String payloadHash = computeHashString(payloadJson);
            AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(userId)
                .actorType(AuditEvent.ActorType.USER)
                .action(action)
                .entityType(entityType)
                .entityId(entityId.toString())
                .payloadHash(payloadHash)
                .build();

            auditWriter.write(auditEvent);
        } catch (Exception e) {
            System.err.println("Failed to write audit event: " + e.getMessage());
        }
    }

    private void ensureCascadeProofComplete(UUID tenantId, UUID userId, UUID deletionId) {
        Optional<DeletionExecutionPlan> planOpt = latestPlan(tenantId, deletionId);
        if (planOpt.isEmpty()) {
            return;
        }

        DeletionExecutionPlan plan = planOpt.get();
        List<DeletionSystemExecution> executions = executionRepository.findByTenantIdAndPlanId(tenantId, plan.getPlanId());
        if (executions.isEmpty()) {
            return;
        }

        List<Map<String, Object>> incomplete = executions.stream()
                .filter(execution -> !isExecutionProofComplete(execution))
                .map(execution -> Map.<String, Object>of(
                        "executionId", execution.getExecutionId(),
                        "systemKey", execution.getSystemKey(),
                        "status", execution.getExecutionStatus().name(),
                        "proofArtifactId", execution.getProofArtifactId(),
                        "exceptionArtifactId", execution.getExceptionArtifactId()))
                .collect(Collectors.toList());

        if (!incomplete.isEmpty()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("tenantId", tenantId);
            payload.put("deletionId", deletionId);
            payload.put("planId", plan.getPlanId());
            payload.put("planVersion", plan.getPlanVersion());
            payload.put("incompleteExecutions", incomplete);

            writeAudit(tenantId, userId, DeletionCascadeAuditActions.DELETION_PROOF_INCOMPLETE,
                    "DeletionRequest", deletionId, payload);
            writeOutboxEvent(tenantId, DeletionCascadeEventTypes.DELETION_PROOF_INCOMPLETE, payload);

            throw new IllegalStateException("Proof incomplete for cascade executions");
        }
    }

    private boolean isExecutionProofComplete(DeletionSystemExecution execution) {
        if (execution.getExecutionStatus() == DeletionSystemExecutionStatus.SUCCEEDED) {
            return execution.getProofArtifactId() != null;
        }
        if (execution.getExecutionStatus() == DeletionSystemExecutionStatus.EXCEPTION_GRANTED) {
            return execution.getExceptionArtifactId() != null;
        }
        return false;
    }

    private Map<String, Object> buildCascadeCompletionPayload(UUID tenantId, UUID deletionId, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("deletionId", deletionId);
        payload.put("status", status);

        latestPlan(tenantId, deletionId).ifPresent(plan -> {
            payload.put("planId", plan.getPlanId());
            payload.put("planVersion", plan.getPlanVersion());
            payload.put("planHash", plan.getPlanHashSha256());
        });

        return payload;
    }

    private Optional<DeletionExecutionPlan> latestPlan(UUID tenantId, UUID deletionId) {
        return planRepository.findByTenantIdAndDeletionIdOrderByPlanVersionDesc(tenantId, deletionId)
                .stream()
                .findFirst();
    }

    private List<Map<String, Object>> buildExecutionArtifacts(UUID tenantId, UUID deletionId, DeletionExecutionPlan plan) {
        if (plan == null) {
            return List.of();
        }

        List<DeletionSystemExecution> executions = executionRepository.findByTenantIdAndPlanId(tenantId, plan.getPlanId());
        return executions.stream()
                .map(execution -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("executionId", execution.getExecutionId());
                    map.put("systemKey", execution.getSystemKey());
                    map.put("status", execution.getExecutionStatus().name());
                    map.put("proofArtifactId", execution.getProofArtifactId());
                    map.put("exceptionArtifactId", execution.getExceptionArtifactId());
                    map.put("manualProofTaskId", execution.getManualProofTaskId());
                    return map;
                })
                .collect(Collectors.toList());
    }

    private void writeOutboxEvent(UUID tenantId, String eventType, Map<String, Object> payload) {
        EventEnvelopeV1 event = EventFactory.create(
            eventType,
            "retention-deletion-service",
            "deletion_request",
            payload.get("deletionId") != null ? payload.get("deletionId").toString() : "",
            payload
        );
        outboxWriter.write(event);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String computeHash(byte[] bytes) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute hash", e);
        }
    }

    private String computeHashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
