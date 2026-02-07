package com.regulyn.retention.model;

import java.time.Instant;
import java.util.UUID;

public class DeletionExceptionResponse {
    private UUID exceptionId;
    private UUID deletionId;
    private UUID planId;
    private UUID executionId;
    private String exceptionType;
    private String status;
    private Instant notBefore;
    private UUID exceptionArtifactId;
    private String executionStatus;

    public UUID getExceptionId() { return exceptionId; }
    public void setExceptionId(UUID exceptionId) { this.exceptionId = exceptionId; }

    public UUID getDeletionId() { return deletionId; }
    public void setDeletionId(UUID deletionId) { this.deletionId = deletionId; }

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }

    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID executionId) { this.executionId = executionId; }

    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getNotBefore() { return notBefore; }
    public void setNotBefore(Instant notBefore) { this.notBefore = notBefore; }

    public UUID getExceptionArtifactId() { return exceptionArtifactId; }
    public void setExceptionArtifactId(UUID exceptionArtifactId) { this.exceptionArtifactId = exceptionArtifactId; }

    public String getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(String executionStatus) { this.executionStatus = executionStatus; }
}