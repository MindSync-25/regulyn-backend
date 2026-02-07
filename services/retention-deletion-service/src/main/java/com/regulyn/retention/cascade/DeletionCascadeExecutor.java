package com.regulyn.retention.cascade;

import com.regulyn.retention.connector.ConnectorServiceClient;
import com.regulyn.retention.connector.ConnectorServiceClientException;
import com.regulyn.retention.connector.ConnectorServiceUnavailableException;
import com.regulyn.retention.connector.GetDeletionJobStatusResponse;
import com.regulyn.retention.connector.StartDeletionJobRequest;
import com.regulyn.retention.connector.StartDeletionJobResponse;
import com.regulyn.retention.entity.DeletionRequest;
import com.regulyn.retention.entity.DeletionSystemExecution;
import com.regulyn.retention.enums.DeletionSystemExecutionStatus;
import com.regulyn.retention.repository.DeletionRequestRepository;
import com.regulyn.retention.repository.DeletionSystemExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DeletionCascadeExecutor {

    private final DeletionSystemExecutionRepository executionRepository;
    private final DeletionRequestRepository deletionRequestRepository;
    private final ConnectorServiceClient connectorServiceClient;
    private final CascadeEventWriter eventWriter;

    public DeletionCascadeExecutor(
            DeletionSystemExecutionRepository executionRepository,
            DeletionRequestRepository deletionRequestRepository,
            ConnectorServiceClient connectorServiceClient,
            CascadeEventWriter eventWriter) {
        this.executionRepository = executionRepository;
        this.deletionRequestRepository = deletionRequestRepository;
        this.connectorServiceClient = connectorServiceClient;
        this.eventWriter = eventWriter;
    }

    @Transactional
    public void kickoffPendingExecutions(UUID tenantId, UUID actorId, UUID planId, String requestIdempotencyKey) {
        List<DeletionSystemExecution> executions = executionRepository.findByTenantIdAndPlanId(tenantId, planId);
        for (DeletionSystemExecution execution : executions) {
            if (execution.getExecutionStatus() != DeletionSystemExecutionStatus.PENDING) {
                continue;
            }

                String execIdempotency = requestIdempotencyKey + ":" + execution.getSystemKey() + ":" + execution.getSubjectRef();
                transitionToRunning(tenantId, actorId, execution, execIdempotency);

            DeletionRequest deletion = deletionRequestRepository.findByDeletionIdAndTenantId(execution.getDeletionId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Deletion request not found"));

            StartDeletionJobRequest request = new StartDeletionJobRequest();
            request.setTenantId(tenantId);
            request.setDeletionId(execution.getDeletionId());
            request.setSystemKey(execution.getSystemKey());
            request.setSubjectType(deletion.getSubjectType());
            request.setSubjectRef(execution.getSubjectRef());
            request.setEntityType(deletion.getEntityType());
            request.setRequestIdempotencyKey(execIdempotency);

            try {
                StartDeletionJobResponse response = connectorServiceClient.startDeletionJob(
                        tenantId,
                        actorId,
                        execIdempotency,
                        request
                );
                if (response == null || response.getJobId() == null || response.getJobId().isBlank()) {
                    markRetryableFailure(tenantId, actorId, execution, "CONNECTOR_INVALID_RESPONSE",
                            "Connector returned empty jobId", execIdempotency);
                } else {
                    execution.setExternalJobRef(response.getJobId());
                    executionRepository.save(execution);
                }
            } catch (ConnectorServiceUnavailableException e) {
                markRetryableFailure(tenantId, actorId, execution, "CONNECTOR_UNAVAILABLE", e.getMessage(), execIdempotency);
            } catch (ConnectorServiceClientException e) {
                markTerminalFailure(tenantId, actorId, execution, "CONNECTOR_CLIENT_ERROR", e.getMessage(), execIdempotency);
            } catch (Exception e) {
                markRetryableFailure(tenantId, actorId, execution, "CONNECTOR_ERROR", e.getMessage(), execIdempotency);
            }
        }
    }

    @Transactional
    public void refreshFromConnector(UUID tenantId, UUID actorId, UUID executionId) {
        DeletionSystemExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found"));
        if (!tenantId.equals(execution.getTenantId())) {
            throw new IllegalArgumentException("Execution does not belong to tenant");
        }
        if (execution.getExternalJobRef() == null || execution.getExternalJobRef().isBlank()) {
            return;
        }

        GetDeletionJobStatusResponse response = connectorServiceClient.getDeletionJobStatus(
                tenantId,
                actorId,
                "refresh:" + executionId,
                execution.getExternalJobRef()
        );
        if (response == null || response.getStatus() == null) {
            return;
        }

        String refreshIdempotency = "refresh:" + executionId;
        switch (response.getStatus()) {
            case "SUCCEEDED" -> markSucceeded(tenantId, actorId, execution, response, refreshIdempotency);
            case "FAILED_RETRYABLE" -> markRetryableFailure(tenantId, actorId, execution,
                response.getErrorCode(), response.getErrorMessage(), refreshIdempotency);
            case "FAILED_TERMINAL" -> markTerminalFailure(tenantId, actorId, execution,
                response.getErrorCode(), response.getErrorMessage(), refreshIdempotency);
            default -> {
                // QUEUED/RUNNING - no state change
            }
        }
    }

    private void transitionToRunning(UUID tenantId, UUID actorId, DeletionSystemExecution execution, String idempotencyKey) {
        execution.setExecutionStatus(DeletionSystemExecutionStatus.RUNNING);
        if (execution.getStartedAt() == null) {
            execution.setStartedAt(Instant.now());
        }
        executionRepository.save(execution);

        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("actorId", actorId);
        payload.put("deletionId", execution.getDeletionId());
        payload.put("planId", execution.getPlanId());
        payload.put("executionId", execution.getExecutionId());
        payload.put("systemKey", execution.getSystemKey());
        payload.put("subjectRef", execution.getSubjectRef());
        payload.put("status", execution.getExecutionStatus().name());
        payload.put("attemptCount", execution.getAttemptCount());
        payload.put("externalJobRef", execution.getExternalJobRef());

        eventWriter.writeAudit(tenantId, actorId, DeletionCascadeAuditActions.DELETION_SYSTEM_STARTED,
                "DeletionSystemExecution", execution.getExecutionId(), payload);
        eventWriter.writeOutbox(tenantId, actorId, DeletionCascadeEventTypes.DELETION_SYSTEM_STARTED,
                "deletion_system_execution", execution.getExecutionId(), payload, idempotencyKey);
    }

    private void markRetryableFailure(UUID tenantId, UUID actorId, DeletionSystemExecution execution, String errorCode,
                                      String errorMessage, String idempotencyKey) {
        int attempt = execution.getAttemptCount() + 1;
        execution.setAttemptCount(attempt);
        execution.setExecutionStatus(DeletionSystemExecutionStatus.FAILED_RETRYABLE);
        execution.setLastErrorCode(errorCode);
        execution.setLastErrorMessage(errorMessage);
        execution.setLastErrorAt(Instant.now());
        execution.setNextRetryAt(calculateNextRetryAt(Instant.now(), attempt));
        executionRepository.save(execution);

        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("actorId", actorId);
        payload.put("deletionId", execution.getDeletionId());
        payload.put("planId", execution.getPlanId());
        payload.put("executionId", execution.getExecutionId());
        payload.put("systemKey", execution.getSystemKey());
        payload.put("subjectRef", execution.getSubjectRef());
        payload.put("status", execution.getExecutionStatus().name());
        payload.put("attemptCount", execution.getAttemptCount());
        payload.put("nextRetryAt", execution.getNextRetryAt());
        payload.put("externalJobRef", execution.getExternalJobRef());
        payload.put("errorCode", errorCode);
        payload.put("errorMessage", errorMessage);

        eventWriter.writeAudit(tenantId, actorId, DeletionCascadeAuditActions.DELETION_SYSTEM_FAILED_RETRYABLE,
                "DeletionSystemExecution", execution.getExecutionId(), payload);
        eventWriter.writeOutbox(tenantId, actorId, DeletionCascadeEventTypes.DELETION_SYSTEM_FAILED_RETRYABLE,
                "deletion_system_execution", execution.getExecutionId(), payload, idempotencyKey);
    }

    private void markTerminalFailure(UUID tenantId, UUID actorId, DeletionSystemExecution execution, String errorCode,
                                     String errorMessage, String idempotencyKey) {
        execution.setExecutionStatus(DeletionSystemExecutionStatus.FAILED_TERMINAL);
        execution.setFinishedAt(Instant.now());
        execution.setLastErrorCode(errorCode);
        execution.setLastErrorMessage(errorMessage);
        execution.setLastErrorAt(Instant.now());
        executionRepository.save(execution);

        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("actorId", actorId);
        payload.put("deletionId", execution.getDeletionId());
        payload.put("planId", execution.getPlanId());
        payload.put("executionId", execution.getExecutionId());
        payload.put("systemKey", execution.getSystemKey());
        payload.put("subjectRef", execution.getSubjectRef());
        payload.put("status", execution.getExecutionStatus().name());
        payload.put("attemptCount", execution.getAttemptCount());
        payload.put("externalJobRef", execution.getExternalJobRef());
        payload.put("errorCode", errorCode);
        payload.put("errorMessage", errorMessage);

        eventWriter.writeAudit(tenantId, actorId, DeletionCascadeAuditActions.DELETION_SYSTEM_FAILED_TERMINAL,
                "DeletionSystemExecution", execution.getExecutionId(), payload);
        eventWriter.writeOutbox(tenantId, actorId, DeletionCascadeEventTypes.DELETION_SYSTEM_FAILED_TERMINAL,
                "deletion_system_execution", execution.getExecutionId(), payload, idempotencyKey);
    }

    private void markSucceeded(UUID tenantId, UUID actorId, DeletionSystemExecution execution,
                               GetDeletionJobStatusResponse response, String idempotencyKey) {
        execution.setExecutionStatus(DeletionSystemExecutionStatus.SUCCEEDED);
        execution.setFinishedAt(Instant.now());
        if (response.getProofArtifactId() != null) {
            execution.setProofArtifactId(response.getProofArtifactId());
        }
        executionRepository.save(execution);

        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("actorId", actorId);
        payload.put("deletionId", execution.getDeletionId());
        payload.put("planId", execution.getPlanId());
        payload.put("executionId", execution.getExecutionId());
        payload.put("systemKey", execution.getSystemKey());
        payload.put("subjectRef", execution.getSubjectRef());
        payload.put("status", execution.getExecutionStatus().name());
        payload.put("attemptCount", execution.getAttemptCount());
        payload.put("externalJobRef", execution.getExternalJobRef());
        payload.put("proofArtifactId", response.getProofArtifactId());

        eventWriter.writeAudit(tenantId, actorId, DeletionCascadeAuditActions.DELETION_SYSTEM_SUCCEEDED,
                "DeletionSystemExecution", execution.getExecutionId(), payload);
        eventWriter.writeOutbox(tenantId, actorId, DeletionCascadeEventTypes.DELETION_SYSTEM_SUCCEEDED,
                "deletion_system_execution", execution.getExecutionId(), payload, idempotencyKey);
    }

    private Instant calculateNextRetryAt(Instant now, int attempt) {
        long baseSeconds = Duration.ofMinutes(5).getSeconds();
        long maxSeconds = Duration.ofHours(6).getSeconds();
        long delay = Math.min(baseSeconds * (long) Math.pow(2, Math.max(attempt - 1, 0)), maxSeconds);
        return now.plusSeconds(delay);
    }
}
