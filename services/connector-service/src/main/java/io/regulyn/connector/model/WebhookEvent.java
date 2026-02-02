package io.regulyn.connector.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Webhook event received from external provider.
 */
@Entity
@Table(name = "webhook_events", schema = "connector")
public class WebhookEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "webhook_event_id")
    private UUID webhookEventId;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "connector_id", nullable = false)
    private UUID connectorId;
    
    @Column(name = "provider", nullable = false)
    private String provider;
    
    @Column(name = "event_type", nullable = false)
    private String eventType;
    
    @Column(name = "normalized_event_type")
    private String normalizedEventType;
    
    @Column(name = "signature")
    private String signature;
    
    @Column(name = "signature_verified", nullable = false)
    private boolean signatureVerified = false;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> rawPayload;
    
    @Column(name = "processed", nullable = false)
    private boolean processed = false;
    
    @Column(name = "processed_at")
    private Instant processedAt;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    
    // Getters and Setters
    public UUID getWebhookEventId() {
        return webhookEventId;
    }
    
    public void setWebhookEventId(UUID webhookEventId) {
        this.webhookEventId = webhookEventId;
    }
    
    public UUID getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }
    
    public UUID getConnectorId() {
        return connectorId;
    }
    
    public void setConnectorId(UUID connectorId) {
        this.connectorId = connectorId;
    }
    
    public String getProvider() {
        return provider;
    }
    
    public void setProvider(String provider) {
        this.provider = provider;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    public String getNormalizedEventType() {
        return normalizedEventType;
    }
    
    public void setNormalizedEventType(String normalizedEventType) {
        this.normalizedEventType = normalizedEventType;
    }
    
    public String getSignature() {
        return signature;
    }
    
    public void setSignature(String signature) {
        this.signature = signature;
    }
    
    public boolean isSignatureVerified() {
        return signatureVerified;
    }
    
    public void setSignatureVerified(boolean signatureVerified) {
        this.signatureVerified = signatureVerified;
    }
    
    public Map<String, Object> getRawPayload() {
        return rawPayload;
    }
    
    public void setRawPayload(Map<String, Object> rawPayload) {
        this.rawPayload = rawPayload;
    }
    
    public boolean isProcessed() {
        return processed;
    }
    
    public void setProcessed(boolean processed) {
        this.processed = processed;
    }
    
    public Instant getProcessedAt() {
        return processedAt;
    }
    
    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
