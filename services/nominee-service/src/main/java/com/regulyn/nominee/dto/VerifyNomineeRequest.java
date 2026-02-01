package com.regulyn.nominee.dto;

import jakarta.validation.constraints.NotNull;

public class VerifyNomineeRequest {

    @NotNull(message = "method is required")
    private VerificationMethod method;

    private String notes;

    public enum VerificationMethod {
        EMAIL_OTP, DOC_CHECK, MANUAL
    }

    // Getters and Setters
    public VerificationMethod getMethod() {
        return method;
    }

    public void setMethod(VerificationMethod method) {
        this.method = method;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
