package com.regulyn.retention.model;

import jakarta.validation.constraints.NotBlank;

public class TransitionDeletionRequest {
    
    @NotBlank(message = "Target status is required")
    private String toStatus; // IN_PROGRESS|FAILED|COMPLETED
    
    private String reason;
    
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
