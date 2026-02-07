package com.regulyn.retention.cascade;

public final class DeletionCascadeAuditActions {
    private DeletionCascadeAuditActions() {}

    public static final String DELETION_PLAN_CREATED = "DELETION_PLAN_CREATED";
    public static final String DELETION_CASCADE_STARTED = "DELETION_CASCADE_STARTED";
    public static final String DELETION_SYSTEM_STARTED = "DELETION_SYSTEM_STARTED";
    public static final String DELETION_SYSTEM_FAILED_RETRYABLE = "DELETION_SYSTEM_FAILED_RETRYABLE";
    public static final String DELETION_SYSTEM_FAILED_TERMINAL = "DELETION_SYSTEM_FAILED_TERMINAL";
    public static final String DELETION_SYSTEM_SUCCEEDED = "DELETION_SYSTEM_SUCCEEDED";
    public static final String DELETION_SYSTEM_MANUAL_REQUIRED = "DELETION_SYSTEM_MANUAL_REQUIRED";

    public static final String DELETION_MANUAL_PROOF_REQUESTED = "DELETION_MANUAL_PROOF_REQUESTED";
    public static final String DELETION_MANUAL_PROOF_SUBMITTED = "DELETION_MANUAL_PROOF_SUBMITTED";
    public static final String DELETION_MANUAL_PROOF_APPROVED = "DELETION_MANUAL_PROOF_APPROVED";
    public static final String DELETION_MANUAL_PROOF_REJECTED = "DELETION_MANUAL_PROOF_REJECTED";
    public static final String DELETION_PROOF_INCOMPLETE = "DELETION_PROOF_INCOMPLETE";
    public static final String DELETION_EXCEPTION_GRANTED = "DELETION_EXCEPTION_GRANTED";
    public static final String DELETION_TOMBSTONED = "DELETION_TOMBSTONED";
    public static final String DELETION_TOMBSTONE_REMOVED = "DELETION_TOMBSTONE_REMOVED";
    public static final String DELETION_COMPLETED = "DELETION_COMPLETED";
}
