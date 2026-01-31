package com.regulyn.retention.model;

import jakarta.validation.constraints.NotBlank;

public class ApproveDeletionRequest {
    
    @NotBlank(message = "Decision is required")
    private String decision; // APPROVE|REJECT
    
    private String reason;
    
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
