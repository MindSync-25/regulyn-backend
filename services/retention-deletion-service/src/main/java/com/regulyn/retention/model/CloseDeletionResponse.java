package com.regulyn.retention.model;

import java.util.UUID;

public class CloseDeletionResponse {
    
    private UUID deletionId;
    private String status;
    private UUID evidenceBundleId;
    
    public CloseDeletionResponse() {}
    
    public CloseDeletionResponse(UUID deletionId, String status, UUID evidenceBundleId) {
        this.deletionId = deletionId;
        this.status = status;
        this.evidenceBundleId = evidenceBundleId;
    }
    
    public UUID getDeletionId() { return deletionId; }
    public void setDeletionId(UUID deletionId) { this.deletionId = deletionId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public UUID getEvidenceBundleId() { return evidenceBundleId; }
    public void setEvidenceBundleId(UUID evidenceBundleId) { this.evidenceBundleId = evidenceBundleId; }
}
