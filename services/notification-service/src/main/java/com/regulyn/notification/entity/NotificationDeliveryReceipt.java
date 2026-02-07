package com.regulyn.notification.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "notification_delivery_receipts",
    schema = "notification",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_notification_delivery_receipts_tenant_hash", columnNames = {"tenant_id", "payload_hash_hex"})
    }
)
public class NotificationDeliveryReceipt {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "notification_message_id", nullable = false)
    private UUID notificationMessageId;
    
    @Column(name = "provider", nullable = false, columnDefinition = "TEXT")
    private String provider;
    
    @Column(name = "provider_message_id", columnDefinition = "TEXT")
    private String providerMessageId;
    
    @Column(name = "status", nullable = false, columnDefinition = "TEXT")
    private String status;
    
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;
    
    @Column(name = "occurred_at")
    private Instant occurredAt;
    
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
    
    @Column(name = "payload_hash", nullable = false, columnDefinition = "BYTEA")
    private byte[] payloadHash;
    
    @Column(name = "payload_hash_hex", nullable = false, columnDefinition = "TEXT")
    private String payloadHashHex;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "receipt_payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode receiptPayload;
    
    @Column(name = "receipt_evidence_artifact_ref", columnDefinition = "TEXT")
    private String receiptEvidenceArtifactRef;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
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
    
    public UUID getNotificationMessageId() {
        return notificationMessageId;
    }
    
    public void setNotificationMessageId(UUID notificationMessageId) {
        this.notificationMessageId = notificationMessageId;
    }
    
    public String getProvider() {
        return provider;
    }
    
    public void setProvider(String provider) {
        this.provider = provider;
    }
    
    public String getProviderMessageId() {
        return providerMessageId;
    }
    
    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getFailureReason() {
        return failureReason;
    }
    
    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
    
    public Instant getOccurredAt() {
        return occurredAt;
    }
    
    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
    
    public Instant getReceivedAt() {
        return receivedAt;
    }
    
    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
    
    public byte[] getPayloadHash() {
        return payloadHash;
    }
    
    public void setPayloadHash(byte[] payloadHash) {
        this.payloadHash = payloadHash;
    }
    
    public String getPayloadHashHex() {
        return payloadHashHex;
    }
    
    public void setPayloadHashHex(String payloadHashHex) {
        this.payloadHashHex = payloadHashHex;
    }
    
    public JsonNode getReceiptPayload() {
        return receiptPayload;
    }
    
    public void setReceiptPayload(JsonNode receiptPayload) {
        this.receiptPayload = receiptPayload;
    }
    
    public String getReceiptEvidenceArtifactRef() {
        return receiptEvidenceArtifactRef;
    }
    
    public void setReceiptEvidenceArtifactRef(String receiptEvidenceArtifactRef) {
        this.receiptEvidenceArtifactRef = receiptEvidenceArtifactRef;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
