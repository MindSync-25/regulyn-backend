package com.regulyn.events.factory;

import com.fasterxml.jackson.databind.JsonNode;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.events.model.ActorType;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.util.EventHasher;
import com.regulyn.events.util.EventJson;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Factory for creating EventEnvelopeV1 instances with proper context
 */
public class EventFactory {
    
    private EventFactory() {
        // Utility class
    }

    /**
     * Create an event envelope with context from TenantContext and MDC
     * @param eventType the event type (e.g., "consent.created")
     * @param sourceService the source service name (e.g., "consent-service")
     * @param entityType the entity type (e.g., "CONSENT")
     * @param entityId the entity identifier
     * @param payload the event payload
     * @return populated EventEnvelopeV1
     */
    public static EventEnvelopeV1 create(String eventType, String sourceService, 
                                         String entityType, String entityId, 
                                         Object payload) {
        return create(eventType, sourceService, entityType, entityId, payload, null);
    }

    /**
     * Create an event envelope with context and optional idempotency key
     * @param eventType the event type (e.g., "consent.created")
     * @param sourceService the source service name (e.g., "consent-service")
     * @param entityType the entity type (e.g., "CONSENT")
     * @param entityId the entity identifier
     * @param payload the event payload
     * @param idempotencyKey optional idempotency key
     * @return populated EventEnvelopeV1
     */
    public static EventEnvelopeV1 create(String eventType, String sourceService, 
                                         String entityType, String entityId, 
                                         Object payload, String idempotencyKey) {
        // Get tenant context
        TenantContext context = TenantContextHolder.getContext();
        
        // Convert payload to JsonNode and compute hash
        JsonNode payloadNode = EventJson.toJsonNode(payload);
        String canonicalJson = EventJson.toCanonicalJson(payload);
        String payloadHash = EventHasher.sha256(canonicalJson);
        
        // Get correlation ID from MDC (requestId/traceId)
        String correlationId = MDC.get("requestId");
        if (correlationId == null) {
            correlationId = MDC.get("traceId");
        }
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        
        // Determine actor type
        ActorType actorType = determineActorType(context);
        
        // Create metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("createdBy", "EventFactory");
        metadata.put("timestamp", Instant.now().toString());
        
        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setEventType(eventType);
        envelope.setTenantId(context.getTenantId());
        envelope.setActorId(context.getUserId());
        envelope.setActorType(actorType);
        envelope.setSourceService(sourceService);
        envelope.setEntityType(entityType);
        envelope.setEntityId(entityId);
        envelope.setOccurredAt(Instant.now());
        envelope.setCorrelationId(correlationId);
        envelope.setIdempotencyKey(idempotencyKey);
        envelope.setPayload(payloadNode);
        envelope.setPayloadHash(payloadHash);
        envelope.setSchemaVersion(1);
        envelope.setMetadata(metadata);
        
        return envelope;
    }

    private static ActorType determineActorType(TenantContext context) {
        if (context.getUserId() != null) {
            return ActorType.USER;
        }
        // Check if it's an API key based context (you can enhance this logic)
        return ActorType.SYSTEM;
    }
}
