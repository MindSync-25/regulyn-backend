package com.regulyn.nominee.dto;

public class VerifyNomineeRejectRequest {

    private VerifyNomineeRequest.VerificationMethod method;

    private String notes;

    private String rejectionReason;

    public VerifyNomineeRequest.VerificationMethod getMethod() {
        return method;
    }

    public void setMethod(VerifyNomineeRequest.VerificationMethod method) {
        this.method = method;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
