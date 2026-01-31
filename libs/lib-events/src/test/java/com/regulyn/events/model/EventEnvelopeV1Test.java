package com.regulyn.events.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.regulyn.events.util.EventJson;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventEnvelopeV1Test {

    @Test
    void eventEnvelope_shouldHaveRequiredFields() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.now();
        JsonNode payload = EventJson.toJsonNode(Map.of("key", "value"));
        
        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(eventId);
        envelope.setEventType("test.created");
        envelope.setTenantId(tenantId);
        envelope.setActorId(actorId);
        envelope.setActorType(ActorType.USER);
        envelope.setSourceService("test-service");
        envelope.setEntityType("TEST");
        envelope.setEntityId("test-123");
        envelope.setOccurredAt(now);
        envelope.setCorrelationId("corr-123");
        envelope.setPayload(payload);
        envelope.setPayloadHash("abc123");
        envelope.setSchemaVersion(1);
        
        assertEquals(eventId, envelope.getEventId());
        assertEquals("test.created", envelope.getEventType());
        assertEquals(tenantId, envelope.getTenantId());
        assertEquals(actorId, envelope.getActorId());
        assertEquals(ActorType.USER, envelope.getActorType());
        assertEquals("test-service", envelope.getSourceService());
        assertEquals("TEST", envelope.getEntityType());
        assertEquals("test-123", envelope.getEntityId());
        assertEquals(now, envelope.getOccurredAt());
        assertEquals("corr-123", envelope.getCorrelationId());
        assertEquals(payload, envelope.getPayload());
        assertEquals("abc123", envelope.getPayloadHash());
        assertEquals(1, envelope.getSchemaVersion());
    }

    @Test
    void eventEnvelope_schemaVersionDefaultsToOne() {
        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        assertEquals(1, envelope.getSchemaVersion());
    }
}
