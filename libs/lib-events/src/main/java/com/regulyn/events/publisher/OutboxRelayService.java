package com.regulyn.events.publisher;

import com.regulyn.events.config.OutboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Background service that periodically relays pending outbox events to external systems
 */
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private final OutboxPublisher publisher;
    private final OutboxProperties properties;

    public OutboxRelayService(OutboxPublisher publisher, OutboxProperties properties) {
        this.publisher = publisher;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${regulyn.outbox.relay.pollingIntervalMs:5000}")
    public void relayPendingEvents() {
        if (!properties.isEnabled()) {
            return;
        }

        log.debug("Polling for pending outbox events (batch size: {})", properties.getBatchSize());
        
        // Delegate to the OutboxPublisher implementation which handles batch publishing
        int published = publisher.publishPendingBatch(properties.getBatchSize());
        
        if (published > 0) {
            log.info("Successfully published {} events", published);
        }
    }
}
