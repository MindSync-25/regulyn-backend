package com.regulyn.retention.service;

import com.regulyn.retention.cascade.CascadeEventWriter;
import com.regulyn.retention.cascade.DeletionCascadeAuditActions;
import com.regulyn.retention.cascade.DeletionCascadeEventTypes;
import com.regulyn.retention.entity.DeletionBackupException;
import com.regulyn.retention.entity.DeletionSystemExecution;
import com.regulyn.retention.enums.DeletionBackupExceptionStatus;
import com.regulyn.retention.enums.DeletionSystemExecutionStatus;
import com.regulyn.retention.integration.EvidenceServiceClient;
import com.regulyn.retention.model.DeletionExceptionGrantRequest;
import com.regulyn.retention.model.DeletionExceptionResponse;
import com.regulyn.retention.repository.DeletionBackupExceptionRepository;
import com.regulyn.retention.repository.DeletionSystemExecutionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeletionExceptionService {

    private final DeletionSystemExecutionRepository executionRepository;
    private final DeletionBackupExceptionRepository exceptionRepository;
    private final EvidenceServiceClient evidenceServiceClient;
    private final CascadeEventWriter eventWriter;

    public DeletionExceptionService(
            DeletionSystemExecutionRepository executionRepository,
            DeletionBackupExceptionRepository exceptionRepository,
            EvidenceServiceClient evidenceServiceClient,
            CascadeEventWriter eventWriter) {
        this.executionRepository = executionRepository;
        this.exceptionRepository = exceptionRepository;
        this.evidenceServiceClient = evidenceServiceClient;
        this.eventWriter = eventWriter;
    }

    @Transactional
    public DeletionExceptionResponse grantException(UUID tenantId, UUID actorId, UUID deletionId, UUID executionId, DeletionExceptionGrantRequest request) {
        DeletionSystemExecution execution = executionRepository.findByTenantIdAndExecutionId(tenantId, executionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "System execution not found"));

        if (!execution.getDeletionId().equals(deletionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution does not belong to deletion");
        }

        if (execution.getExecutionStatus() == DeletionSystemExecutionStatus.SUCCEEDED) {
            throw new IllegalStateException("Execution already succeeded");
        }

        Optional<DeletionBackupException> existing = exceptionRepository.findByTenantIdAndExecutionIdAndStatus(
                tenantId,
                executionId,
                DeletionBackupExceptionStatus.ACTIVE
        );
        if (existing.isPresent()) {
            DeletionBackupException active = existing.get();
            if (active.getExceptionType() == request.getExceptionType()
                    && active.getNotBefore().equals(request.getNotBefore())) {
                return toResponse(active, execution);
            }
            throw new IllegalStateException("Active exception already exists for execution");
        }

        String hash = computeHash(request.getExceptionType().name() + "|" + request.getReason() + "|" + request.getNotBefore());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("deletionId", deletionId);
        metadata.put("planId", execution.getPlanId());
        metadata.put("executionId", executionId);
        metadata.put("exceptionType", request.getExceptionType().name());
        metadata.put("reason", request.getReason());
        metadata.put("notBefore", request.getNotBefore().toString());

        UUID artifactId = evidenceServiceClient.createArtifact(
                tenantId,
                actorId,
                "DELETION_EXCEPTION",
                "SYSTEM_EXECUTION",
                executionId,
                hash,
                null,
                metadata
        ).resolveArtifactId();

        DeletionBackupException exception = new DeletionBackupException();
        exception.setTenantId(tenantId);
        exception.setDeletionId(deletionId);
        exception.setPlanId(execution.getPlanId());
        exception.setExecutionId(executionId);
        exception.setExceptionType(request.getExceptionType());
        exception.setReason(request.getReason());
        exception.setNotBefore(request.getNotBefore());
        exception.setStatus(DeletionBackupExceptionStatus.ACTIVE);
        exception.setExceptionArtifactId(artifactId);
        exception.setGrantedAt(Instant.now());
        exception.setGrantedBy(actorId);
        exception.setCreatedBy(actorId);
        exception.setUpdatedBy(actorId);

        exception = exceptionRepository.save(exception);

        execution.setExecutionStatus(DeletionSystemExecutionStatus.EXCEPTION_GRANTED);
        execution.setExceptionArtifactId(artifactId);
        execution.setFinishedAt(Instant.now());
        execution.setUpdatedBy(actorId);
        executionRepository.save(execution);

        writeExceptionGrantedEvents(tenantId, actorId, exception, execution);

        return toResponse(exception, execution);
    }

    private void writeExceptionGrantedEvents(UUID tenantId, UUID actorId, DeletionBackupException exception, DeletionSystemExecution execution) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("actorId", actorId);
        payload.put("deletionId", exception.getDeletionId());
        payload.put("planId", exception.getPlanId());
        payload.put("executionId", exception.getExecutionId());
        payload.put("exceptionId", exception.getExceptionId());
        payload.put("exceptionType", exception.getExceptionType().name());
        payload.put("notBefore", exception.getNotBefore().toString());
        payload.put("artifactId", exception.getExceptionArtifactId());
        payload.put("executionStatus", execution.getExecutionStatus().name());

        eventWriter.writeAudit(
                tenantId,
                actorId,
                DeletionCascadeAuditActions.DELETION_EXCEPTION_GRANTED,
                "DeletionBackupException",
                exception.getExceptionId(),
                payload
        );
        eventWriter.writeOutbox(
                tenantId,
                actorId,
                DeletionCascadeEventTypes.DELETION_EXCEPTION_GRANTED,
                "DeletionBackupException",
                exception.getExceptionId(),
                payload,
                "exception-granted:" + exception.getExceptionId()
        );
    }

    private DeletionExceptionResponse toResponse(DeletionBackupException exception, DeletionSystemExecution execution) {
        DeletionExceptionResponse response = new DeletionExceptionResponse();
        response.setExceptionId(exception.getExceptionId());
        response.setExecutionId(execution.getExecutionId());
        response.setDeletionId(exception.getDeletionId());
        response.setPlanId(exception.getPlanId());
        response.setExceptionType(exception.getExceptionType().name());
        response.setStatus(exception.getStatus().name());
        response.setNotBefore(exception.getNotBefore());
        response.setExceptionArtifactId(exception.getExceptionArtifactId());
        response.setExecutionStatus(execution.getExecutionStatus().name());
        return response;
    }

    private String computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute hash", e);
        }
    }
}