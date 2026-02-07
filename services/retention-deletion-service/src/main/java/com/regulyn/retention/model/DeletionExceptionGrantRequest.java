package com.regulyn.retention.model;

import com.regulyn.retention.enums.DeletionBackupExceptionType;

import java.time.Instant;

public class DeletionExceptionGrantRequest {
    private DeletionBackupExceptionType exceptionType;
    private String reason;
    private Instant notBefore;

    public DeletionBackupExceptionType getExceptionType() { return exceptionType; }
    public void setExceptionType(DeletionBackupExceptionType exceptionType) { this.exceptionType = exceptionType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getNotBefore() { return notBefore; }
    public void setNotBefore(Instant notBefore) { this.notBefore = notBefore; }
}