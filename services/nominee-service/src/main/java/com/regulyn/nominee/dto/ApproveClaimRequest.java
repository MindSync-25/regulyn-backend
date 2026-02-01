package com.regulyn.nominee.dto;

import jakarta.validation.constraints.NotNull;

public class ApproveClaimRequest {

    @NotNull(message = "decision is required")
    private Decision decision;

    private String notes;

    public enum Decision {
        APPROVE, REJECT
    }

    // Getters and Setters
    public Decision getDecision() {
        return decision;
    }

    public void setDecision(Decision decision) {
        this.decision = decision;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
