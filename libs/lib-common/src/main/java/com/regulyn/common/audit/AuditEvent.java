package com.regulyn.common.audit;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model for audit events.
 * Represents an immutable record of an action performed in the system.
 */
public class AuditEvent {
    
    private final UUID eventId;
    private final UUID tenantId;
    private final UUID actorId;
    private final ActorType actorType;
    private final String service;
    private final String action;
    private final String entityType;
    private final String entityId;
    private final Instant timestamp;
    private final String payloadHash;
    private final UUID evidenceId;
    private final JsonNode metadata;
    
    private AuditEvent(Builder builder) {
        this.eventId = builder.eventId;
        this.tenantId = builder.tenantId;
        this.actorId = builder.actorId;
        this.actorType = builder.actorType;
        this.service = builder.service;
        this.action = builder.action;
        this.entityType = builder.entityType;
        this.entityId = builder.entityId;
        this.timestamp = builder.timestamp;
        this.payloadHash = builder.payloadHash;
        this.evidenceId = builder.evidenceId;
        this.metadata = builder.metadata;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public UUID getEventId() { return eventId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getActorId() { return actorId; }
    public ActorType getActorType() { return actorType; }
    public String getService() { return service; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public Instant getTimestamp() { return timestamp; }
    public String getPayloadHash() { return payloadHash; }
    public UUID getEvidenceId() { return evidenceId; }
    public JsonNode getMetadata() { return metadata; }
    
    public static class Builder {
        private UUID eventId = UUID.randomUUID();
        private UUID tenantId;
        private UUID actorId;
        private ActorType actorType = ActorType.SYSTEM;
        private String service;
        private String action;
        private String entityType;
        private String entityId;
        private Instant timestamp = Instant.now();
        private String payloadHash;
        private UUID evidenceId;
        private JsonNode metadata;
        
        public Builder eventId(UUID eventId) {
            this.eventId = eventId;
            return this;
        }
        
        public Builder tenantId(UUID tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        public Builder actorId(UUID actorId) {
            this.actorId = actorId;
            return this;
        }
        
        public Builder actorType(ActorType actorType) {
            this.actorType = actorType;
            return this;
        }
        
        public Builder service(String service) {
            this.service = service;
            return this;
        }
        
        public Builder action(String action) {
            this.action = action;
            return this;
        }
        
        public Builder entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }
        
        public Builder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }
        
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder payloadHash(String payloadHash) {
            this.payloadHash = payloadHash;
            return this;
        }
        
        public Builder evidenceId(UUID evidenceId) {
            this.evidenceId = evidenceId;
            return this;
        }
        
        public Builder metadata(JsonNode metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public AuditEvent build() {
            if (tenantId == null) {
                throw new IllegalStateException("tenantId is required");
            }
            if (action == null || action.isBlank()) {
                throw new IllegalStateException("action is required");
            }
            if (entityType == null || entityType.isBlank()) {
                throw new IllegalStateException("entityType is required");
            }
            if (entityId == null || entityId.isBlank()) {
                throw new IllegalStateException("entityId is required");
            }
            if (payloadHash == null || payloadHash.isBlank()) {
                throw new IllegalStateException("payloadHash is required");
            }
            return new AuditEvent(this);
        }
    }
    
    public enum ActorType {
        USER,
        API_KEY,
        SYSTEM
    }
}
