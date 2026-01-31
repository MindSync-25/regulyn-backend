package com.regulyn.events.publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * No-op implementation of OutboxPublisher
 * This is used until Kafka integration is implemented
 */
@Service
public class DefaultNoopOutboxPublisher implements OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(DefaultNoopOutboxPublisher.class);

    @Override
    public int publishPendingBatch(int maxEvents) {
        log.debug("DefaultNoopOutboxPublisher: publishPendingBatch called with maxEvents={} (no-op)", maxEvents);
        // No-op: Kafka publisher will be implemented later
        return 0;
    }
}
