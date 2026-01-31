package com.regulyn.dsar.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CreateBundleRequest {
    private String bundleType;
    private String referenceType;
    private String referenceId;
    private String title;
    private String description;
    private List<String> evidenceIds;
    private List<String> artifactIds;
    private Map<String, Object> metadata;

    public CreateBundleRequest() {
    }

    public CreateBundleRequest(String bundleType, String referenceType, String referenceId, 
                                String title, List<String> evidenceIds) {
        this.bundleType = bundleType;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.title = title;
        this.evidenceIds = evidenceIds;
    }

    // Getters and setters
    public String getBundleType() {
        return bundleType;
    }

    public void setBundleType(String bundleType) {
        this.bundleType = bundleType;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getEvidenceIds() {
        return evidenceIds;
    }

    public void setEvidenceIds(List<String> evidenceIds) {
        this.evidenceIds = evidenceIds;
    }

    public List<String> getArtifactIds() {
        return artifactIds;
    }

    public void setArtifactIds(List<String> artifactIds) {
        this.artifactIds = artifactIds;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
