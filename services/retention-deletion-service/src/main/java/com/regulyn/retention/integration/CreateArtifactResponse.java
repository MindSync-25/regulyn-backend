package com.regulyn.retention.integration;

import java.util.UUID;

public class CreateArtifactResponse {
    private UUID artifactId;
    private UUID evidenceId;

    public UUID getArtifactId() { return artifactId; }
    public void setArtifactId(UUID artifactId) { this.artifactId = artifactId; }

    public UUID getEvidenceId() { return evidenceId; }
    public void setEvidenceId(UUID evidenceId) { this.evidenceId = evidenceId; }

    public UUID resolveArtifactId() {
        return artifactId != null ? artifactId : evidenceId;
    }
}