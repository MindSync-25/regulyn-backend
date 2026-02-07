package com.regulyn.retention.model;

import java.time.Instant;
import java.util.UUID;

public class ManualProofTaskResponse {
    private UUID taskId;
    private UUID deletionId;
    private UUID planId;
    private UUID executionId;
    private String taskStatus;
    private String executionStatus;
    private String requestedReason;
    private Instant requestedAt;
    private Instant submittedAt;
    private Instant decidedAt;
    private UUID submissionArtifactId;
    private String submissionHashSha256;

    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }

    public UUID getDeletionId() { return deletionId; }
    public void setDeletionId(UUID deletionId) { this.deletionId = deletionId; }

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }

    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID executionId) { this.executionId = executionId; }

    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }

    public String getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(String executionStatus) { this.executionStatus = executionStatus; }

    public String getRequestedReason() { return requestedReason; }
    public void setRequestedReason(String requestedReason) { this.requestedReason = requestedReason; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    public UUID getSubmissionArtifactId() { return submissionArtifactId; }
    public void setSubmissionArtifactId(UUID submissionArtifactId) { this.submissionArtifactId = submissionArtifactId; }

    public String getSubmissionHashSha256() { return submissionHashSha256; }
    public void setSubmissionHashSha256(String submissionHashSha256) { this.submissionHashSha256 = submissionHashSha256; }
}