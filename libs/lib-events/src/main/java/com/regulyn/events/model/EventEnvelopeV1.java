package com.regulyn.events.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event Envelope v1 - standardized event structure for all domain events
 */
public class EventEnvelopeV1 {
    private UUID eventId;
    private String eventType;
    private UUID tenantId;
    private UUID actorId;
    private ActorType actorType;
    private String sourceService;
    private String entityType;
    private String entityId;
    private Instant occurredAt;
    private String correlationId;
    private String idempotencyKey;
    private JsonNode payload;
    private String payloadHash;
    private int schemaVersion = 1;
    private Map<String, Object> metadata;

    public EventEnvelopeV1() {
    }

    public EventEnvelopeV1(UUID eventId, String eventType, UUID tenantId, UUID actorId, 
                          ActorType actorType, String sourceService, String entityType, 
                          String entityId, Instant occurredAt, String correlationId, 
                          String idempotencyKey, JsonNode payload, String payloadHash, 
                          int schemaVersion, Map<String, Object> metadata) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.actorType = actorType;
        this.sourceService = sourceService;
        this.entityType = entityType;
        this.entityId = entityId;
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
        this.idempotencyKey = idempotencyKey;
        this.payload = payload;
        this.payloadHash = payloadHash;
        this.schemaVersion = schemaVersion;
        this.metadata = metadata;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public void setActorType(ActorType actorType) {
        this.actorType = actorType;
    }

    public String getSourceService() {
        return sourceService;
    }

    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
