package com.regulyn.dsar.model;

import jakarta.validation.constraints.NotBlank;

public class ApproveDsarRequest {
    
    @NotBlank(message = "decision is required")
    private String decision; // APPROVE or REJECT
    
    private String reason;

    // Getters and setters
    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
