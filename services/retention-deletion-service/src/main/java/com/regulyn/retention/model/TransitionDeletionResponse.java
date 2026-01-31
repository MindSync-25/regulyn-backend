package com.regulyn.retention.model;

import java.util.UUID;

public class TransitionDeletionResponse {
    
    private UUID deletionId;
    private String status;
    
    public TransitionDeletionResponse() {}
    
    public TransitionDeletionResponse(UUID deletionId, String status) {
        this.deletionId = deletionId;
        this.status = status;
    }
    
    public UUID getDeletionId() { return deletionId; }
    public void setDeletionId(UUID deletionId) { this.deletionId = deletionId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
