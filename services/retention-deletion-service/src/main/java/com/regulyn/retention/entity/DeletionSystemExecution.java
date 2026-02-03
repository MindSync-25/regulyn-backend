package com.regulyn.retention.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-system execution tracking with connector integration.
 * Manages status, retries, and proof artifacts for each system in the deletion cascade.
 */
@Entity
@Table(name = "deletion_system_execution", schema = "deletion")
public class DeletionSystemExecution {

    @Id
    @Column(name = "exec_id")
    private UUID execId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "deletion_request_id", nullable = false)
    private UUID deletionRequestId;

    @Column(name = "system_name", nullable = false, length = 120)
    private String systemName;

    @Column(name = "connector_id")
    private UUID connectorId;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "action_type", nullable = false, length = 32)
    private String actionType = "DELETE";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ExecutionStatus status;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 5;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column(name = "connector_run_id")
    private UUID connectorRunId;

    @Column(name = "connector_job_id")
    private UUID connectorJobId;

    @Column(name = "proof_artifact_ref", length = 200)
    private String proofArtifactRef;

    @Column(name = "exception_artifact_ref", length = 200)
    private String exceptionArtifactRef;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum ExecutionStatus {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED_RETRYABLE,
        FAILED_TERMINAL,
        MANUAL_REQUIRED,
        EXCEPTION_GRANTED
    }

    @PrePersist
    protected void onCreate() {
        if (execId == null) {
            execId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Constructors
    public DeletionSystemExecution() {}

    public DeletionSystemExecution(UUID tenantId, UUID deletionRequestId, String systemName) {
        this.tenantId = tenantId;
        this.deletionRequestId = deletionRequestId;
        this.systemName = systemName;
        this.status = ExecutionStatus.PENDING;
    }

    // Getters and Setters
    public UUID getExecId() {
        return execId;
    }

    public void setExecId(UUID execId) {
        this.execId = execId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getDeletionRequestId() {
        return deletionRequestId;
    }

    public void setDeletionRequestId(UUID deletionRequestId) {
        this.deletionRequestId = deletionRequestId;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public UUID getConnectorId() {
        return connectorId;
    }

    public void setConnectorId(UUID connectorId) {
        this.connectorId = connectorId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public UUID getConnectorRunId() {
        return connectorRunId;
    }

    public void setConnectorRunId(UUID connectorRunId) {
        this.connectorRunId = connectorRunId;
    }

    public UUID getConnectorJobId() {
        return connectorJobId;
    }

    public void setConnectorJobId(UUID connectorJobId) {
        this.connectorJobId = connectorJobId;
    }

    public String getProofArtifactRef() {
        return proofArtifactRef;
    }

    public void setProofArtifactRef(String proofArtifactRef) {
        this.proofArtifactRef = proofArtifactRef;
    }

    public String getExceptionArtifactRef() {
        return exceptionArtifactRef;
    }

    public void setExceptionArtifactRef(String exceptionArtifactRef) {
        this.exceptionArtifactRef = exceptionArtifactRef;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
