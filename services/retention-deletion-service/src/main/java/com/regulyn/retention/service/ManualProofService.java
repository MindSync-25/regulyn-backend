package com.regulyn.retention.service;

import com.regulyn.retention.cascade.CascadeEventWriter;
import com.regulyn.retention.cascade.DeletionCascadeAuditActions;
import com.regulyn.retention.cascade.DeletionCascadeEventTypes;
import com.regulyn.retention.entity.DeletionManualProofTask;
import com.regulyn.retention.entity.DeletionSystemExecution;
import com.regulyn.retention.enums.DeletionManualProofTaskStatus;
import com.regulyn.retention.enums.DeletionSystemExecutionStatus;
import com.regulyn.retention.integration.EvidenceServiceClient;
import com.regulyn.retention.model.ManualProofDecisionRequest;
import com.regulyn.retention.model.ManualProofTaskResponse;
import com.regulyn.retention.repository.DeletionManualProofTaskRepository;
import com.regulyn.retention.repository.DeletionSystemExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ManualProofService {

    private static final Logger logger = LoggerFactory.getLogger(ManualProofService.class);

    private final DeletionSystemExecutionRepository executionRepository;
    private final DeletionManualProofTaskRepository taskRepository;
    private final ManualProofArtifactStore artifactStore;
    private final EvidenceServiceClient evidenceServiceClient;
    private final CascadeEventWriter eventWriter;

    public ManualProofService(
            DeletionSystemExecutionRepository executionRepository,
            DeletionManualProofTaskRepository taskRepository,
            ManualProofArtifactStore artifactStore,
            EvidenceServiceClient evidenceServiceClient,
            CascadeEventWriter eventWriter) {
        this.executionRepository = executionRepository;
        this.taskRepository = taskRepository;
        this.artifactStore = artifactStore;
        this.evidenceServiceClient = evidenceServiceClient;
        this.eventWriter = eventWriter;
    }

    @Transactional
    public ManualProofTaskResponse requestManualProof(UUID tenantId, UUID actorId, UUID deletionId, UUID executionId, String reason) {
        DeletionSystemExecution execution = getExecutionOrThrow(tenantId, deletionId, executionId);

        if (execution.getExecutionStatus() == DeletionSystemExecutionStatus.SUCCEEDED
                || execution.getExecutionStatus() == DeletionSystemExecutionStatus.EXCEPTION_GRANTED) {
            throw new IllegalStateException("Execution already completed");
        }

        Optional<DeletionManualProofTask> existing = taskRepository.findByTenantIdAndExecutionId(tenantId, executionId);
        if (existing.isPresent()) {
            DeletionManualProofTask task = existing.get();
            if (execution.getExecutionStatus() != DeletionSystemExecutionStatus.MANUAL_REQUIRED) {
                execution.setExecutionStatus(DeletionSystemExecutionStatus.MANUAL_REQUIRED);
                execution.setUpdatedBy(actorId);
                executionRepository.save(execution);
                writeSystemManualRequiredEvents(tenantId, actorId, execution);
            }
            return toResponse(task, execution);
        }

        DeletionManualProofTask task = new DeletionManualProofTask();
        task.setTenantId(tenantId);
        task.setDeletionId(deletionId);
        task.setPlanId(execution.getPlanId());
        task.setExecutionId(executionId);
        task.setRequestedReason(reason);
        task.setRequestedBy(actorId);
        task.setCreatedBy(actorId);
        task.setUpdatedBy(actorId);

        task = taskRepository.save(task);

        execution.setManualProofTaskId(task.getTaskId());
        boolean statusChanged = execution.getExecutionStatus() != DeletionSystemExecutionStatus.MANUAL_REQUIRED;
        if (statusChanged) {
            execution.setExecutionStatus(DeletionSystemExecutionStatus.MANUAL_REQUIRED);
        }
        execution.setUpdatedBy(actorId);
        executionRepository.save(execution);

        if (statusChanged) {
            writeSystemManualRequiredEvents(tenantId, actorId, execution);
        }
        writeManualProofRequestedEvents(tenantId, actorId, task, execution);

        return toResponse(task, execution);
    }

    @Transactional
    public ManualProofTaskResponse submitManualProof(
            UUID tenantId,
            UUID actorId,
            UUID deletionId,
            UUID executionId,
            MultipartFile file,
            String notes) throws IOException {

        DeletionSystemExecution execution = getExecutionOrThrow(tenantId, deletionId, executionId);
        DeletionManualProofTask task = taskRepository.findByTenantIdAndExecutionId(tenantId, executionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manual proof task not found"));

        if (task.getTaskStatus() == DeletionManualProofTaskStatus.APPROVED) {
            throw new IllegalStateException("Manual proof task already approved");
        }

        if (task.getTaskStatus() == DeletionManualProofTaskStatus.SUBMITTED && task.getSubmissionArtifactId() != null) {
            return toResponse(task, execution);
        }

        boolean wasRejected = task.getTaskStatus() == DeletionManualProofTaskStatus.REJECTED;

        byte[] fileBytes = file.getBytes();
        String hash = computeHash(fileBytes);

        String storedFilename = sanitizeFilename(file.getOriginalFilename());
        String storageRef = artifactStore.store(tenantId, executionId, storedFilename, fileBytes, file.getContentType());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("deletionId", deletionId);
        metadata.put("planId", execution.getPlanId());
        metadata.put("executionId", executionId);
        metadata.put("taskId", task.getTaskId());
        metadata.put("filename", file.getOriginalFilename());
        metadata.put("contentType", file.getContentType());
        metadata.put("sizeBytes", file.getSize());
        if (notes != null && !notes.isBlank()) {
            metadata.put("notes", notes);
        }

        UUID artifactId;
        try {
            artifactId = evidenceServiceClient.createArtifact(
                tenantId,
                actorId,
                "MANUAL_PROOF",
                "DELETION_SYSTEM_EXECUTION",
                executionId,
                hash,
                storageRef,
                metadata
            ).resolveArtifactId();
        } catch (RuntimeException ex) {
            logger.warn("Evidence artifact creation failed after file store. tenantId={}, executionId={}, storageRef={}",
                tenantId, executionId, storageRef, ex);
            throw ex;
        }

        task.setSubmissionArtifactId(artifactId);
        task.setSubmissionHashSha256(hash);
        task.setSubmittedAt(Instant.now());
        task.setSubmittedBy(actorId);
        task.setTaskStatus(DeletionManualProofTaskStatus.SUBMITTED);
        task.setUpdatedBy(actorId);

        if (wasRejected) {
            task.setReviewerComment(null);
            task.setDecidedAt(null);
            task.setDecidedBy(null);
        }

        task = taskRepository.save(task);

        writeManualProofSubmittedEvents(tenantId, actorId, task, execution, storageRef, notes);

        return toResponse(task, execution);
    }

    @Transactional
    public ManualProofTaskResponse decideManualProof(
            UUID tenantId,
            UUID actorId,
            UUID deletionId,
            UUID executionId,
            ManualProofDecisionRequest request) {

        DeletionSystemExecution execution = getExecutionOrThrow(tenantId, deletionId, executionId);
        DeletionManualProofTask task = taskRepository.findByTenantIdAndExecutionId(tenantId, executionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manual proof task not found"));

        if (task.getTaskStatus() == DeletionManualProofTaskStatus.APPROVED
                || task.getTaskStatus() == DeletionManualProofTaskStatus.REJECTED) {
            return toResponse(task, execution);
        }

        if (task.getTaskStatus() != DeletionManualProofTaskStatus.SUBMITTED) {
            throw new IllegalStateException("Manual proof task is not submitted");
        }

        if ("APPROVE".equalsIgnoreCase(request.getDecision())) {
            task.setTaskStatus(DeletionManualProofTaskStatus.APPROVED);
            task.setDecidedAt(Instant.now());
            task.setDecidedBy(actorId);
            task.setReviewerComment(request.getComment());
            task.setUpdatedBy(actorId);

            execution.setExecutionStatus(DeletionSystemExecutionStatus.SUCCEEDED);
            execution.setProofArtifactId(task.getSubmissionArtifactId());
            execution.setFinishedAt(Instant.now());
            execution.setUpdatedBy(actorId);
            executionRepository.save(execution);

            taskRepository.save(task);

            writeManualProofApprovedEvents(tenantId, actorId, task, execution);

        } else if ("REJECT".equalsIgnoreCase(request.getDecision())) {
            task.setTaskStatus(DeletionManualProofTaskStatus.REJECTED);
            task.setDecidedAt(Instant.now());
            task.setDecidedBy(actorId);
            task.setReviewerComment(request.getComment());
            task.setUpdatedBy(actorId);
            taskRepository.save(task);

            writeManualProofRejectedEvents(tenantId, actorId, task, execution);
        } else {
            throw new IllegalArgumentException("Invalid decision: " + request.getDecision());
        }

        return toResponse(task, execution);
    }

    private DeletionSystemExecution getExecutionOrThrow(UUID tenantId, UUID deletionId, UUID executionId) {
        DeletionSystemExecution execution = executionRepository.findByTenantIdAndExecutionId(tenantId, executionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "System execution not found"));
        if (!execution.getDeletionId().equals(deletionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution does not belong to deletion");
        }
        return execution;
    }

    private ManualProofTaskResponse toResponse(DeletionManualProofTask task, DeletionSystemExecution execution) {
        ManualProofTaskResponse response = new ManualProofTaskResponse();
        response.setTaskId(task.getTaskId());
        response.setDeletionId(task.getDeletionId());
        response.setPlanId(task.getPlanId());
        response.setExecutionId(task.getExecutionId());
        response.setTaskStatus(task.getTaskStatus().name());
        response.setSubmissionArtifactId(task.getSubmissionArtifactId());
        response.setSubmissionHashSha256(task.getSubmissionHashSha256());
        response.setRequestedReason(task.getRequestedReason());
        response.setRequestedAt(task.getRequestedAt());
        response.setSubmittedAt(task.getSubmittedAt());
        response.setDecidedAt(task.getDecidedAt());
        response.setExecutionStatus(execution.getExecutionStatus().name());
        return response;
    }

    private void writeSystemManualRequiredEvents(UUID tenantId, UUID actorId, DeletionSystemExecution execution) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("actorId", actorId);
        payload.put("deletionId", execution.getDeletionId());
        payload.put("planId", execution.getPlanId());
        payload.put("executionId", execution.getExecutionId());
        payload.put("executionStatus", execution.getExecutionStatus().name());

        eventWriter.writeAudit(
                tenantId,
                actorId,
                DeletionCascadeAuditActions.DELETION_SYSTEM_MANUAL_REQUIRED,
                "DeletionSystemExecution",
                execution.getExecutionId(),
                payload
        );
        eventWriter.writeOutbox(
                tenantId,
                actorId,
                DeletionCascadeEventTypes.DELETION_SYSTEM_MANUAL_REQUIRED,
                "DeletionSystemExecution",
                execution.getExecutionId(),
                payload,
                "manual-required:" + execution.getExecutionId()
        );
    }

    private void writeManualProofRequestedEvents(UUID tenantId, UUID actorId, DeletionManualProofTask task, DeletionSystemExecution execution) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("actorId", actorId);
        payload.put("deletionId", task.getDeletionId());
        payload.put("planId", task.getPlanId());
        payload.put("executionId", task.getExecutionId());
        payload.put("taskId", task.getTaskId());
        payload.put("reason", task.getRequestedReason());
        payload.put("executionStatus", execution.getExecutionStatus().name());

        eventWriter.writeAudit(
                tenantId,
                actorId,
                DeletionCascadeAuditActions.DELETION_MANUAL_PROOF_REQUESTED,
                "DeletionManualProofTask",
                task.getTaskId(),
                payload
        );
        eventWriter.writeOutbox(
                tenantId,
                actorId,
                DeletionCascadeEventTypes.DELETION_MANUAL_PROOF_REQUESTED,
                "DeletionManualProofTask",
                task.getTaskId(),
                payload,
                "manual-proof-requested:" + task.getTaskId()
        );
    }

    private void writeManualProofSubmittedEvents(
            UUID tenantId,
            UUID actorId,
            DeletionManualProofTask task,
            DeletionSystemExecution execution,
            String storageRef,
            String notes) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("actorId", actorId);
        payload.put("deletionId", task.getDeletionId());
        payload.put("planId", task.getPlanId());
        payload.put("executionId", task.getExecutionId());
        payload.put("taskId", task.getTaskId());
        payload.put("artifactId", task.getSubmissionArtifactId());
        payload.put("hash", task.getSubmissionHashSha256());
        payload.put("storageRef", storageRef);
        payload.put("notes", notes);

        eventWriter.writeAudit(
                tenantId,
                actorId,
                DeletionCascadeAuditActions.DELETION_MANUAL_PROOF_SUBMITTED,
                "DeletionManualProofTask",
                task.getTaskId(),
                payload
        );
        eventWriter.writeOutbox(
                tenantId,
                actorId,
                DeletionCascadeEventTypes.DELETION_MANUAL_PROOF_SUBMITTED,
                "DeletionManualProofTask",
                task.getTaskId(),
                payload,
                "manual-proof-submitted:" + task.getTaskId()
        );
    }

    private void writeManualProofApprovedEvents(UUID tenantId, UUID actorId, DeletionManualProofTask task, DeletionSystemExecution execution) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("actorId", actorId);
        payload.put("deletionId", task.getDeletionId());
        payload.put("planId", task.getPlanId());
        payload.put("executionId", task.getExecutionId());
        payload.put("taskId", task.getTaskId());
        payload.put("artifactId", task.getSubmissionArtifactId());

        eventWriter.writeAudit(
                tenantId,
                actorId,
                DeletionCascadeAuditActions.DELETION_MANUAL_PROOF_APPROVED,
                "DeletionManualProofTask",
                task.getTaskId(),
                payload
        );
        eventWriter.writeOutbox(
                tenantId,
                actorId,
                DeletionCascadeEventTypes.DELETION_MANUAL_PROOF_APPROVED,
                "DeletionManualProofTask",
                task.getTaskId(),
                payload,
                "manual-proof-approved:" + task.getTaskId()
        );

        Map<String, Object> systemPayload = new HashMap<>(payload);
        systemPayload.put("executionStatus", execution.getExecutionStatus().name());

        eventWriter.writeAudit(
                tenantId,
                actorId,
                DeletionCascadeAuditActions.DELETION_SYSTEM_SUCCEEDED,
                "DeletionSystemExecution",
                execution.getExecutionId(),
                systemPayload
        );
        eventWriter.writeOutbox(
                tenantId,
                actorId,
                DeletionCascadeEventTypes.DELETION_SYSTEM_SUCCEEDED,
                "DeletionSystemExecution",
                execution.getExecutionId(),
                systemPayload,
                "system-succeeded:" + execution.getExecutionId()
        );
    }

    private void writeManualProofRejectedEvents(UUID tenantId, UUID actorId, DeletionManualProofTask task, DeletionSystemExecution execution) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("actorId", actorId);
        payload.put("deletionId", task.getDeletionId());
        payload.put("planId", task.getPlanId());
        payload.put("executionId", task.getExecutionId());
        payload.put("taskId", task.getTaskId());
        payload.put("comment", task.getReviewerComment());

        eventWriter.writeAudit(
                tenantId,
                actorId,
                DeletionCascadeAuditActions.DELETION_MANUAL_PROOF_REJECTED,
                "DeletionManualProofTask",
                task.getTaskId(),
                payload
        );
        eventWriter.writeOutbox(
                tenantId,
                actorId,
                DeletionCascadeEventTypes.DELETION_MANUAL_PROOF_REJECTED,
                "DeletionManualProofTask",
                task.getTaskId(),
                payload,
                "manual-proof-rejected:" + task.getTaskId()
        );

        eventWriter.writeAudit(
                tenantId,
                actorId,
                DeletionCascadeAuditActions.DELETION_PROOF_INCOMPLETE,
                "DeletionSystemExecution",
                execution.getExecutionId(),
                payload
        );
        eventWriter.writeOutbox(
                tenantId,
                actorId,
                DeletionCascadeEventTypes.DELETION_PROOF_INCOMPLETE,
                "DeletionSystemExecution",
                execution.getExecutionId(),
                payload,
                "proof-incomplete:" + execution.getExecutionId()
        );
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
            throw new IllegalStateException("Failed to compute hash", e);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "proof";
        }
        return filename.replaceAll("[/\\\\]", "_");
    }
}