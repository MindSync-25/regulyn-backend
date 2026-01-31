package com.regulyn.events.publisher;

/**
 * Interface for publishing outbox events to external systems (e.g., Kafka)
 * Implementations will be plugged in later when Kafka is integrated
 */
public interface OutboxPublisher {

    /**
     * Publish a batch of pending events
     * @param maxEvents maximum number of events to publish in this batch
     * @return number of events successfully published
     */
    int publishPendingBatch(int maxEvents);
}
