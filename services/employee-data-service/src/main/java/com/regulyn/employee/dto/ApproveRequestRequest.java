package com.regulyn.employee.dto;

import jakarta.validation.constraints.NotNull;

public class ApproveRequestRequest {

    public enum Decision {
        APPROVE, REJECT
    }

    @NotNull(message = "Decision is required")
    private Decision decision;

    private String reason;

    public Decision getDecision() {
        return decision;
    }

    public void setDecision(Decision decision) {
        this.decision = decision;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
