package com.regulyn.retention.connector;

import java.util.UUID;

public class GetDeletionJobStatusResponse {
    private String jobId;
    private String status;
    private UUID proofArtifactId;
    private String errorCode;
    private String errorMessage;

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getProofArtifactId() { return proofArtifactId; }
    public void setProofArtifactId(UUID proofArtifactId) { this.proofArtifactId = proofArtifactId; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
