package com.regulyn.retention.integration;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CreateBundleRequest {
    private String bundleType;
    private String referenceType;
    private UUID referenceId;
    private List<UUID> evidenceIds;
    private Map<String, Object> metadata;

    public String getBundleType() { return bundleType; }
    public void setBundleType(String bundleType) { this.bundleType = bundleType; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    public UUID getReferenceId() { return referenceId; }
    public void setReferenceId(UUID referenceId) { this.referenceId = referenceId; }

    public List<UUID> getEvidenceIds() { return evidenceIds; }
    public void setEvidenceIds(List<UUID> evidenceIds) { this.evidenceIds = evidenceIds; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
