package com.regulyn.retention.enums;

public enum DeletionSystemExecutionStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED_RETRYABLE,
    FAILED_TERMINAL,
    EXCEPTION_GRANTED,
    MANUAL_REQUIRED
}
