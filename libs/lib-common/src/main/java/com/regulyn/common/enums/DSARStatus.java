package com.regulyn.common.enums;

/**
 * DSAR (Data Subject Access Request) workflow statuses.
 */
public enum DSARStatus {
    RECEIVED,
    ASSIGNED,
    IN_PROGRESS,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    COMPLETED,
    CLOSED
}
