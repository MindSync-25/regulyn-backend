package com.regulyn.events.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.events.model.EventEnvelopeV1;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for writing events to the outbox table
 * This ensures events are persisted transactionally with domain changes
 */
@Service
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Write an event envelope to the outbox
     * Must be called within a transaction to ensure atomicity with domain changes
     * @param envelope the event envelope to write
     * @return the persisted outbox event
     */
    @Transactional
    public OutboxEvent write(EventEnvelopeV1 envelope) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setEventId(envelope.getEventId());
        outboxEvent.setTenantId(envelope.getTenantId());
        outboxEvent.setEventType(envelope.getEventType());
        outboxEvent.setSourceService(envelope.getSourceService());
        outboxEvent.setEntityType(envelope.getEntityType());
        outboxEvent.setEntityId(envelope.getEntityId());
        outboxEvent.setOccurredAt(envelope.getOccurredAt());
        outboxEvent.setCorrelationId(envelope.getCorrelationId());
        outboxEvent.setPayloadHash(envelope.getPayloadHash());
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setAttempts(0);
        
        // Serialize the full envelope as payload
        try {
            String payloadJson = objectMapper.writeValueAsString(envelope);
            outboxEvent.setPayload(payloadJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event envelope", e);
        }
        
        return repository.save(outboxEvent);
    }
}
