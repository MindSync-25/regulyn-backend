package io.regulyn.scanner.dto;

import java.util.UUID;

public class RunEvidenceBundleResponse {

    public enum Status {
        CREATED,
        ALREADY_EXISTS
    }

    private UUID runId;
    private String bundleArtifactRef;
    private String bundleHash;
    private Status status;

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public String getBundleArtifactRef() {
        return bundleArtifactRef;
    }

    public void setBundleArtifactRef(String bundleArtifactRef) {
        this.bundleArtifactRef = bundleArtifactRef;
    }

    public String getBundleHash() {
        return bundleHash;
    }

    public void setBundleHash(String bundleHash) {
        this.bundleHash = bundleHash;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
