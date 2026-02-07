package com.regulyn.retention.entity;

import com.regulyn.retention.enums.DeletionManualProofTaskStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deletion_manual_proof_task", schema = "deletion")
public class DeletionManualProofTask {

    @Id
    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "deletion_id", nullable = false)
    private UUID deletionId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_status", nullable = false)
    private DeletionManualProofTaskStatus taskStatus = DeletionManualProofTaskStatus.OPEN;

    @Column(name = "requested_reason", nullable = false)
    private String requestedReason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "submission_artifact_id")
    private UUID submissionArtifactId;

    @Column(name = "submission_hash_sha256", length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String submissionHashSha256;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "reviewer_comment")
    private String reviewerComment;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

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
        if (taskId == null) {
            taskId = UUID.randomUUID();
        }
        if (taskStatus == null) {
            taskStatus = DeletionManualProofTaskStatus.OPEN;
        }
        if (requestedAt == null) {
            requestedAt = Instant.now();
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

    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getDeletionId() { return deletionId; }
    public void setDeletionId(UUID deletionId) { this.deletionId = deletionId; }

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }

    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID executionId) { this.executionId = executionId; }

    public DeletionManualProofTaskStatus getTaskStatus() { return taskStatus; }
    public void setTaskStatus(DeletionManualProofTaskStatus taskStatus) { this.taskStatus = taskStatus; }

    public String getRequestedReason() { return requestedReason; }
    public void setRequestedReason(String requestedReason) { this.requestedReason = requestedReason; }

    public Instant getRequestedAt() { return requestedAt; }
    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }

    public UUID getSubmissionArtifactId() { return submissionArtifactId; }
    public void setSubmissionArtifactId(UUID submissionArtifactId) { this.submissionArtifactId = submissionArtifactId; }

    public String getSubmissionHashSha256() { return submissionHashSha256; }
    public void setSubmissionHashSha256(String submissionHashSha256) { this.submissionHashSha256 = submissionHashSha256; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public UUID getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(UUID submittedBy) { this.submittedBy = submittedBy; }

    public String getReviewerComment() { return reviewerComment; }
    public void setReviewerComment(String reviewerComment) { this.reviewerComment = reviewerComment; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    public UUID getDecidedBy() { return decidedBy; }
    public void setDecidedBy(UUID decidedBy) { this.decidedBy = decidedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
}
