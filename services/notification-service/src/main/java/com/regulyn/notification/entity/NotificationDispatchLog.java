package com.regulyn.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "notification_dispatch_logs",
    schema = "notification"
)
public class NotificationDispatchLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "dispatch_id")
    private UUID dispatchId;
    
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;
    
    @Column(name = "request_id", nullable = false)
    private UUID requestId;
    
    @Column(name = "recipient_id", nullable = false, length = 100)
    private String recipientId;
    
    @Column(name = "recipient_address", length = 500)
    private String recipientAddress;
    
    @Column(name = "status", nullable = false, length = 50)
    private String status;
    
    @Column(name = "skip_reason", columnDefinition = "TEXT")
    private String skipReason;
    
    @Column(name = "message_subject", length = 500)
    private String messageSubject;
    
    @Column(name = "message_body", columnDefinition = "TEXT")
    private String messageBody;
    
    @Column(name = "message_hash", length = 64)
    private String messageHash;
    
    @Column(name = "dispatched_at", nullable = false, updatable = false)
    private Instant dispatchedAt;
    
    @PrePersist
    protected void onCreate() {
        dispatchedAt = Instant.now();
    }
    
    // Getters and Setters
    public UUID getDispatchId() {
        return dispatchId;
    }
    
    public void setDispatchId(UUID dispatchId) {
        this.dispatchId = dispatchId;
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    
    public UUID getRequestId() {
        return requestId;
    }
    
    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }
    
    public String getRecipientId() {
        return recipientId;
    }
    
    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }
    
    public String getRecipientAddress() {
        return recipientAddress;
    }
    
    public void setRecipientAddress(String recipientAddress) {
        this.recipientAddress = recipientAddress;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getSkipReason() {
        return skipReason;
    }
    
    public void setSkipReason(String skipReason) {
        this.skipReason = skipReason;
    }
    
    public String getMessageSubject() {
        return messageSubject;
    }
    
    public void setMessageSubject(String messageSubject) {
        this.messageSubject = messageSubject;
    }
    
    public String getMessageBody() {
        return messageBody;
    }
    
    public void setMessageBody(String messageBody) {
        this.messageBody = messageBody;
    }
    
    public String getMessageHash() {
        return messageHash;
    }
    
    public void setMessageHash(String messageHash) {
        this.messageHash = messageHash;
    }
    
    public Instant getDispatchedAt() {
        return dispatchedAt;
    }
    
    public void setDispatchedAt(Instant dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }
}
