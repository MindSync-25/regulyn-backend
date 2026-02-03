package com.regulyn.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_receipts", schema = "notification")
public class DeliveryReceipt {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "receipt_id")
    private UUID receiptId;
    
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;
    
    @Column(name = "dispatch_id", nullable = false)
    private UUID dispatchId;
    
    @Column(name = "provider_message_id", nullable = false, length = 500)
    private String providerMessageId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 50)
    private DeliveryStatus deliveryStatus;
    
    @Column(name = "provider_name", nullable = false, length = 100)
    private String providerName;
    
    @Column(name = "provider_event_type", length = 100)
    private String providerEventType;
    
    @Column(name = "provider_callback_data", columnDefinition = "jsonb")
    private String providerCallbackData;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "error_code", length = 100)
    private String errorCode;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "event_time")
    private Instant eventTime;
    
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;
    
    @Column(name = "raw_payload_hash", length = 64)
    private String rawPayloadHash;
    
    @Column(name = "raw_payload_ref", columnDefinition = "TEXT")
    private String rawPayloadRef;
    
    @Column(name = "provider_signature_valid")
    private Boolean providerSignatureValid;
    
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    // Getters and Setters
    
    public UUID getReceiptId() {
        return receiptId;
    }
    
    public void setReceiptId(UUID receiptId) {
        this.receiptId = receiptId;
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    
    public UUID getDispatchId() {
        return dispatchId;
    }
    
    public void setDispatchId(UUID dispatchId) {
        this.dispatchId = dispatchId;
    }
    
    public String getProviderMessageId() {
        return providerMessageId;
    }
    
    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }
    
    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }
    
    public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }
    
    public String getProviderName() {
        return providerName;
    }
    
    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }
    
    public String getProviderEventType() {
        return providerEventType;
    }
    
    public void setProviderEventType(String providerEventType) {
        this.providerEventType = providerEventType;
    }
    
    public String getProviderCallbackData() {
        return providerCallbackData;
    }
    
    public void setProviderCallbackData(String providerCallbackData) {
        this.providerCallbackData = providerCallbackData;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public Instant getEventTime() {
        return eventTime;
    }
    
    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }
    
    public String getFailureReason() {
        return failureReason;
    }
    
    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
    
    public String getRawPayloadHash() {
        return rawPayloadHash;
    }
    
    public void setRawPayloadHash(String rawPayloadHash) {
        this.rawPayloadHash = rawPayloadHash;
    }
    
    public String getRawPayloadRef() {
        return rawPayloadRef;
    }
    
    public void setRawPayloadRef(String rawPayloadRef) {
        this.rawPayloadRef = rawPayloadRef;
    }
    
    public Boolean getProviderSignatureValid() {
        return providerSignatureValid;
    }
    
    public void setProviderSignatureValid(Boolean providerSignatureValid) {
        this.providerSignatureValid = providerSignatureValid;
    }
    
    public enum DeliveryStatus {
        QUEUED,
        SENT,
        DELIVERED,
        FAILED,
        BOUNCED,
        COMPLAINED
    }
}
