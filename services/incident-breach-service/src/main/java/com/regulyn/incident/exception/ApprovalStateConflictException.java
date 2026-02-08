package com.regulyn.incident.exception;

public class ApprovalStateConflictException extends IllegalStateException {
    public ApprovalStateConflictException(String message) {
        super(message);
    }
}
