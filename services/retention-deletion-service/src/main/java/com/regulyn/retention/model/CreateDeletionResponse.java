package com.regulyn.retention.model;

import java.time.Instant;
import java.util.UUID;

public class CreateDeletionResponse {
    
    private UUID deletionId;
    private String status;
    private Instant dueAt;
    
    public CreateDeletionResponse() {}
    
    public CreateDeletionResponse(UUID deletionId, String status, Instant dueAt) {
        this.deletionId = deletionId;
        this.status = status;
        this.dueAt = dueAt;
    }
    
    public UUID getDeletionId() { return deletionId; }
    public void setDeletionId(UUID deletionId) { this.deletionId = deletionId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
}
