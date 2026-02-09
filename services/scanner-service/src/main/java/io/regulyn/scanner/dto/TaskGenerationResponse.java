package io.regulyn.scanner.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TaskGenerationResponse {

    private UUID runId;
    private int createdCount;
    private int skippedCount;
    private List<UUID> existingTaskIds = new ArrayList<>();
    private List<UUID> createdTaskIds = new ArrayList<>();

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public void setCreatedCount(int createdCount) {
        this.createdCount = createdCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    public List<UUID> getExistingTaskIds() {
        return existingTaskIds;
    }

    public void setExistingTaskIds(List<UUID> existingTaskIds) {
        this.existingTaskIds = existingTaskIds;
    }

    public List<UUID> getCreatedTaskIds() {
        return createdTaskIds;
    }

    public void setCreatedTaskIds(List<UUID> createdTaskIds) {
        this.createdTaskIds = createdTaskIds;
    }
}
