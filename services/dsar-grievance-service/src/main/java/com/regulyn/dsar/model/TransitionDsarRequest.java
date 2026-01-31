package com.regulyn.dsar.model;

import jakarta.validation.constraints.NotBlank;

public class TransitionDsarRequest {
    
    @NotBlank(message = "toStatus is required")
    private String toStatus;
    
    private String reason;

    // Getters and setters
    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
