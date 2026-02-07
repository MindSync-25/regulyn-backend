package com.regulyn.retention.cascade;

public class DeletionApprovalRequiredException extends RuntimeException {
    public DeletionApprovalRequiredException(String message) {
        super(message);
    }
}
