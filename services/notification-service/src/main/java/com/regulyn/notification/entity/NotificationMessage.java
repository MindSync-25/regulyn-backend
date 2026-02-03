package com.regulyn.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "notification_messages",
    schema = "notification",
    indexes = {
        @Index(name = "idx_notification_messages_tenant", columnList = "tenant_id"),
        @Index(name = "idx_notification_messages_request", columnList = "request_id"),
        @Index(name = "idx_notification_messages_status", columnList = "status"),
        @Index(name = "idx_notification_messages_hash", columnList = "message_hash"),
        @Index(name = "idx_notification_messages_next_retry", columnList = "next_retry_at"),
        @Index(name = "idx_notification_messages_provider_msg", columnList = "provider_message_id")
    }
)
public class NotificationMessage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "message_id")
    private UUID messageId;
    
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;
    
    @Column(name = "request_id", nullable = false)
    private UUID requestId;
    
    @Column(name = "recipient_id", nullable = false, length = 100)
    private String recipientId;
    
    @Column(name = "recipient_address", nullable = false, length = 500)
    private String recipientAddress;
    
    @Column(name = "channel", nullable = false, length = 50)
    private String channel;
    
    @Column(name = "message_subject", length = 500)
    private String messageSubject;
    
    @Column(name = "message_body", nullable = false, columnDefinition = "TEXT")
    private String messageBody;
    
    @Column(name = "message_format", nullable = false, length = 50)
    private String messageFormat;
    
    @Column(name = "message_hash", nullable = false, length = 64)
    private String messageHash;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private MessageStatus status = MessageStatus.PENDING;
    
    @Column(name = "provider_message_id", length = 500)
    private String providerMessageId;
    
    @Column(name = "provider_name", length = 100)
    private String providerName;
    
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;
    
    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 3;
    
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;
    
    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;
    
    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;
    
    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;
    
    @Column(name = "evidence_artifact_id", length = 200)
    private String evidenceArtifactId;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    // Getters and Setters
    
    public UUID getMessageId() {
        return messageId;
    }
    
    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
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
    
    public String getChannel() {
        return channel;
    }
    
    public void setChannel(String channel) {
        this.channel = channel;
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
    
    public String getMessageFormat() {
        return messageFormat;
    }
    
    public void setMessageFormat(String messageFormat) {
        this.messageFormat = messageFormat;
    }
    
    public String getMessageHash() {
        return messageHash;
    }
    
    public void setMessageHash(String messageHash) {
        this.messageHash = messageHash;
    }
    
    public MessageStatus getStatus() {
        return status;
    }
    
    public void setStatus(MessageStatus status) {
        this.status = status;
    }
    
    public String getProviderMessageId() {
        return providerMessageId;
    }
    
    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }
    
    public String getProviderName() {
        return providerName;
    }
    
    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }
    
    public Integer getAttemptCount() {
        return attemptCount;
    }
    
    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }
    
    public Integer getMaxAttempts() {
        return maxAttempts;
    }
    
    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }
    
    public Instant getNextRetryAt() {
        return nextRetryAt;
    }
    
    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }
    
    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }
    
    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }
    
    public String getLastErrorMessage() {
        return lastErrorMessage;
    }
    
    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }
    
    public String getLastErrorCode() {
        return lastErrorCode;
    }
    
    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }
    
    public String getEvidenceArtifactId() {
        return evidenceArtifactId;
    }
    
    public void setEvidenceArtifactId(String evidenceArtifactId) {
        this.evidenceArtifactId = evidenceArtifactId;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    public enum MessageStatus {
        PENDING,
        QUEUED,
        SENT,
        DELIVERED,
        FAILED_RETRYABLE,
        FAILED_TERMINAL,
        CONSENT_BLOCKED
    }
}
