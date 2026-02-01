package io.regulyn.scanner.dto;

import java.util.UUID;

public class PromoteRetentionCandidatesResponse {

    private UUID runId;
    private Integer promotedCount;
    private Integer failedCount;

    // Getters and Setters
    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public Integer getPromotedCount() {
        return promotedCount;
    }

    public void setPromotedCount(Integer promotedCount) {
        this.promotedCount = promotedCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }
}
