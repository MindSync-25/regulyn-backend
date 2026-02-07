package com.regulyn.retention.entity;

import com.regulyn.retention.enums.DeletionSystemExecutionStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deletion_system_execution", schema = "deletion")
public class DeletionSystemExecution {

    @Id
    @Column(name = "execution_id")
    private UUID executionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "deletion_id", nullable = false)
    private UUID deletionId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "system_key", nullable = false)
    private String systemKey;

    @Column(name = "subject_ref", nullable = false)
    private String subjectRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false)
    private DeletionSystemExecutionStatus executionStatus = DeletionSystemExecutionStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 8;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "external_job_ref")
    private String externalJobRef;

    @Column(name = "last_error_code")
    private String lastErrorCode;

    @Column(name = "last_error_message")
    private String lastErrorMessage;

    @Column(name = "last_error_at")
    private Instant lastErrorAt;

    @Column(name = "proof_artifact_id")
    private UUID proofArtifactId;

    @Column(name = "exception_artifact_id")
    private UUID exceptionArtifactId;

    @Column(name = "manual_proof_task_id")
    private UUID manualProofTaskId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PrePersist
    protected void onCreate() {
        if (executionId == null) {
            executionId = UUID.randomUUID();
        }
        if (attemptCount == null) {
            attemptCount = 0;
        }
        if (maxAttempts == null) {
            maxAttempts = 8;
        }
        if (executionStatus == null) {
            executionStatus = DeletionSystemExecutionStatus.PENDING;
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

    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID executionId) { this.executionId = executionId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getDeletionId() { return deletionId; }
    public void setDeletionId(UUID deletionId) { this.deletionId = deletionId; }

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }

    public String getSystemKey() { return systemKey; }
    public void setSystemKey(String systemKey) { this.systemKey = systemKey; }

    public String getSubjectRef() { return subjectRef; }
    public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }

    public DeletionSystemExecutionStatus getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(DeletionSystemExecutionStatus executionStatus) { this.executionStatus = executionStatus; }

    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }

    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }

    public Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public String getExternalJobRef() { return externalJobRef; }
    public void setExternalJobRef(String externalJobRef) { this.externalJobRef = externalJobRef; }

    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }

    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }

    public Instant getLastErrorAt() { return lastErrorAt; }
    public void setLastErrorAt(Instant lastErrorAt) { this.lastErrorAt = lastErrorAt; }

    public UUID getProofArtifactId() { return proofArtifactId; }
    public void setProofArtifactId(UUID proofArtifactId) { this.proofArtifactId = proofArtifactId; }

    public UUID getExceptionArtifactId() { return exceptionArtifactId; }
    public void setExceptionArtifactId(UUID exceptionArtifactId) { this.exceptionArtifactId = exceptionArtifactId; }

    public UUID getManualProofTaskId() { return manualProofTaskId; }
    public void setManualProofTaskId(UUID manualProofTaskId) { this.manualProofTaskId = manualProofTaskId; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
}
