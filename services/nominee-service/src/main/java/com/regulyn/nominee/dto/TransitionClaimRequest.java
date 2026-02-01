package com.regulyn.nominee.dto;

import jakarta.validation.constraints.NotNull;

public class TransitionClaimRequest {

    @NotNull(message = "toStatus is required")
    private ToStatus toStatus;

    private String reason;

    public enum ToStatus {
        IN_REVIEW, NEEDS_INFO, APPROVED, REJECTED, CLOSED
    }

    // Getters and Setters
    public ToStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(ToStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
