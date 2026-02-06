package io.regulyn.connector.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * WebhookEvent entity for incoming webhook event storage.
 */
@Entity
@Table(name = "webhook_events", schema = "connector")
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "connector_id", nullable = false)
    private UUID connectorId;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "signature_valid", nullable = false)
    private Boolean signatureValid;

    @Type(JsonBinaryType.class)
    @Column(name = "headers_json", columnDefinition = "jsonb")
    private Map<String, Object> headersJson;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Type(JsonBinaryType.class)
    @Column(name = "raw_payload_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> rawPayloadJson;

    @Column(name = "normalized_type", length = 128)
    private String normalizedType;

    @Column(name = "normalized_subject", length = 256)
    private String normalizedSubject;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Boolean getSignatureValid() {
        return signatureValid;
    }

    public void setSignatureValid(Boolean signatureValid) {
        this.signatureValid = signatureValid;
    }

    public Map<String, Object> getHeadersJson() {
        return headersJson;
    }

    public void setHeadersJson(Map<String, Object> headersJson) {
        this.headersJson = headersJson;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public Map<String, Object> getRawPayloadJson() {
        return rawPayloadJson;
    }

    public void setRawPayloadJson(Map<String, Object> rawPayloadJson) {
        this.rawPayloadJson = rawPayloadJson;
    }

    public String getNormalizedType() {
        return normalizedType;
    }

    public void setNormalizedType(String normalizedType) {
        this.normalizedType = normalizedType;
    }

    public String getNormalizedSubject() {
        return normalizedSubject;
    }

    public void setNormalizedSubject(String normalizedSubject) {
        this.normalizedSubject = normalizedSubject;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}
