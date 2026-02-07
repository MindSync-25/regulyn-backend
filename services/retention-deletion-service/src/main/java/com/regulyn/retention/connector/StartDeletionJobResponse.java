package com.regulyn.retention.connector;

import java.time.Instant;

public class StartDeletionJobResponse {
    private String jobId;
    private Instant acceptedAt;

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
}
