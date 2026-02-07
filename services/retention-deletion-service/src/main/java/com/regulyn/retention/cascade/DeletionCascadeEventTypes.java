package com.regulyn.retention.cascade;

public final class DeletionCascadeEventTypes {
    private DeletionCascadeEventTypes() {}

    public static final String DELETION_PLAN_CREATED = "deletion.plan_created";
    public static final String DELETION_CASCADE_STARTED = "deletion.cascade_started";
    public static final String DELETION_SYSTEM_STARTED = "deletion.system_started";
    public static final String DELETION_SYSTEM_FAILED_RETRYABLE = "deletion.system_failed_retryable";
    public static final String DELETION_SYSTEM_FAILED_TERMINAL = "deletion.system_failed_terminal";
    public static final String DELETION_SYSTEM_SUCCEEDED = "deletion.system_succeeded";
    public static final String DELETION_SYSTEM_MANUAL_REQUIRED = "deletion.system_manual_required";

    public static final String DELETION_MANUAL_PROOF_REQUESTED = "deletion.manual_proof_requested";
    public static final String DELETION_MANUAL_PROOF_SUBMITTED = "deletion.manual_proof_submitted";
    public static final String DELETION_MANUAL_PROOF_APPROVED = "deletion.manual_proof_approved";
    public static final String DELETION_MANUAL_PROOF_REJECTED = "deletion.manual_proof_rejected";
    public static final String DELETION_PROOF_INCOMPLETE = "deletion.proof_incomplete";
    public static final String DELETION_EXCEPTION_GRANTED = "deletion.exception_granted";
    public static final String DELETION_TOMBSTONED = "deletion.tombstoned";
    public static final String DELETION_TOMBSTONE_REMOVED = "deletion.tombstone_removed";
    public static final String DELETION_COMPLETED = "deletion.completed";
}
