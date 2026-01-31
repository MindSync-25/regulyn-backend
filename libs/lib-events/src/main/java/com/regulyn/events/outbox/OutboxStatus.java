package com.regulyn.events.outbox;

/**
 * Status of an outbox event
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
