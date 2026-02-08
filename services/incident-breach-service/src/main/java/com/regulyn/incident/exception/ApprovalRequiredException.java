package com.regulyn.incident.exception;

public class ApprovalRequiredException extends IllegalStateException {
    public ApprovalRequiredException(String message) {
        super(message);
    }
}
