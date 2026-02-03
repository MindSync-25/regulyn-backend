package com.regulyn.retention.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Manual proof submission workflow for systems requiring human intervention.
 * Supports maker-checker approval process.
 */
@Entity
@Table(name = "deletion_manual_proof_task", schema = "deletion",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "deletion_request_id", "system_name"}))
public class DeletionManualProofTask {

    @Id
    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "deletion_request_id", nullable = false)
    private UUID deletionRequestId;

    @Column(name = "system_name", nullable = false, length = 120)
    private String systemName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TaskStatus status = TaskStatus.OPEN;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "proof_artifact_ref", length = 200)
    private String proofArtifactRef;

    @Column(name = "reviewer_user_id")
    private UUID reviewerUserId;

    public enum TaskStatus {
        OPEN,
        SUBMITTED,
        APPROVED,
        REJECTED
    }

    @PrePersist
    protected void onCreate() {
        if (taskId == null) {
            taskId = UUID.randomUUID();
        }
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }

    // Constructors
    public DeletionManualProofTask() {}

    public DeletionManualProofTask(UUID tenantId, UUID deletionRequestId, String systemName) {
        this.tenantId = tenantId;
        this.deletionRequestId = deletionRequestId;
        this.systemName = systemName;
        this.status = TaskStatus.OPEN;
    }

    // Getters and Setters
    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
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

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getProofArtifactRef() {
        return proofArtifactRef;
    }

    public void setProofArtifactRef(String proofArtifactRef) {
        this.proofArtifactRef = proofArtifactRef;
    }

    public UUID getReviewerUserId() {
        return reviewerUserId;
    }

    public void setReviewerUserId(UUID reviewerUserId) {
        this.reviewerUserId = reviewerUserId;
    }
}
