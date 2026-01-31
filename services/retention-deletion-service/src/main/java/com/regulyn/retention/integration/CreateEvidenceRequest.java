package com.regulyn.retention.integration;

import java.util.List;
import java.util.UUID;

public class CreateEvidenceRequest {
    private String action;
    private UUID referenceId;
    private UUID subjectId;
    private String entityType;
    private String status;
    private List<String> artifactHashes;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public UUID getReferenceId() { return referenceId; }
    public void setReferenceId(UUID referenceId) { this.referenceId = referenceId; }

    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<String> getArtifactHashes() { return artifactHashes; }
    public void setArtifactHashes(List<String> artifactHashes) { this.artifactHashes = artifactHashes; }
}
