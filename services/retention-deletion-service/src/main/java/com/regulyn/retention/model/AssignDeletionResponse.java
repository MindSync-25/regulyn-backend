package com.regulyn.retention.model;

import java.util.UUID;

public class AssignDeletionResponse {
    
    private UUID deletionId;
    private String status;
    private UUID assignedTo;
    
    public AssignDeletionResponse() {}
    
    public AssignDeletionResponse(UUID deletionId, String status, UUID assignedTo) {
        this.deletionId = deletionId;
        this.status = status;
        this.assignedTo = assignedTo;
    }
    
    public UUID getDeletionId() { return deletionId; }
    public void setDeletionId(UUID deletionId) { this.deletionId = deletionId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public UUID getAssignedTo() { return assignedTo; }
    public void setAssignedTo(UUID assignedTo) { this.assignedTo = assignedTo; }
}
