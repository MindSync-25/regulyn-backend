package com.regulyn.events.publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    public void publish(String eventType, String aggregateId, Object payload) {
        log.info("Event published: type={}, aggregateId={}, payload={}", eventType, aggregateId, payload);
        // In a real implementation, this would write to an outbox table
        // For now, just log the event
    }
}
