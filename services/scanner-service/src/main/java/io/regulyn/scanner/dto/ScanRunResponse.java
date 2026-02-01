package io.regulyn.scanner.dto;

import java.time.Instant;
import java.util.UUID;

public class ScanRunResponse {

    private UUID runId;
    private UUID sourceId;
    private String scanMode;
    private String status;
    private Instant sinceAt;
    private String requestRef;
    private Instant queuedAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Integer findingsCount;
    private String resultHash;
    private String errorMessage;

    // Getters and Setters
    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public String getScanMode() {
        return scanMode;
    }

    public void setScanMode(String scanMode) {
        this.scanMode = scanMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getSinceAt() {
        return sinceAt;
    }

    public void setSinceAt(Instant sinceAt) {
        this.sinceAt = sinceAt;
    }

    public String getRequestRef() {
        return requestRef;
    }

    public void setRequestRef(String requestRef) {
        this.requestRef = requestRef;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public void setQueuedAt(Instant queuedAt) {
        this.queuedAt = queuedAt;
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

    public Integer getFindingsCount() {
        return findingsCount;
    }

    public void setFindingsCount(Integer findingsCount) {
        this.findingsCount = findingsCount;
    }

    public String getResultHash() {
        return resultHash;
    }

    public void setResultHash(String resultHash) {
        this.resultHash = resultHash;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
