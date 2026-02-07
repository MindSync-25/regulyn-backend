package com.regulyn.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "notification_messages",
    schema = "notification",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_notification_messages_tenant_hash", columnNames = {"tenant_id", "message_hash_hex"})
    }
)
public class NotificationMessage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "notification_request_id", nullable = false)
    private UUID notificationRequestId;
    
    @Column(name = "recipient", nullable = false, columnDefinition = "TEXT")
    private String recipient;
    
    @Column(name = "channel", nullable = false, columnDefinition = "TEXT")
    private String channel;
    
    @Column(name = "category", nullable = false, columnDefinition = "TEXT")
    private String category;
    
    @Column(name = "template_key", columnDefinition = "TEXT")
    private String templateKey;
    
    @Column(name = "template_id")
    private UUID templateId;
    
    @Column(name = "template_version_id")
    private UUID templateVersionId;
    
    @Column(name = "language", columnDefinition = "TEXT")
    private String language;
    
    @Column(name = "message_hash", nullable = false, columnDefinition = "BYTEA")
    private byte[] messageHash;
    
    @Column(name = "message_hash_hex", nullable = false, columnDefinition = "TEXT")
    private String messageHashHex;
    
    @Column(name = "status", nullable = false, columnDefinition = "TEXT")
    private String status;
    
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;
    
    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 3;
    
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;
    
    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;
    
    @Column(name = "last_failure_reason", columnDefinition = "TEXT")
    private String lastFailureReason;
    
    @Column(name = "sent_evidence_artifact_ref", columnDefinition = "TEXT")
    private String sentEvidenceArtifactRef;
    
    @Column(name = "delivered_evidence_artifact_ref", columnDefinition = "TEXT")
    private String deliveredEvidenceArtifactRef;
    
    @Column(name = "failed_evidence_artifact_ref", columnDefinition = "TEXT")
    private String failedEvidenceArtifactRef;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
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
    
    public UUID getNotificationRequestId() {
        return notificationRequestId;
    }
    
    public void setNotificationRequestId(UUID notificationRequestId) {
        this.notificationRequestId = notificationRequestId;
    }
    
    public String getRecipient() {
        return recipient;
    }
    
    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }
    
    public String getChannel() {
        return channel;
    }
    
    public void setChannel(String channel) {
        this.channel = channel;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public String getTemplateKey() {
        return templateKey;
    }
    
    public void setTemplateKey(String templateKey) {
        this.templateKey = templateKey;
    }
    
    public UUID getTemplateId() {
        return templateId;
    }
    
    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }
    
    public UUID getTemplateVersionId() {
        return templateVersionId;
    }
    
    public void setTemplateVersionId(UUID templateVersionId) {
        this.templateVersionId = templateVersionId;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public byte[] getMessageHash() {
        return messageHash;
    }
    
    public void setMessageHash(byte[] messageHash) {
        this.messageHash = messageHash;
    }
    
    public String getMessageHashHex() {
        return messageHashHex;
    }
    
    public void setMessageHashHex(String messageHashHex) {
        this.messageHashHex = messageHashHex;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
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
    
    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }
    
    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }
    
    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }
    
    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }
    
    public String getLastFailureReason() {
        return lastFailureReason;
    }
    
    public void setLastFailureReason(String lastFailureReason) {
        this.lastFailureReason = lastFailureReason;
    }
    
    public String getSentEvidenceArtifactRef() {
        return sentEvidenceArtifactRef;
    }
    
    public void setSentEvidenceArtifactRef(String sentEvidenceArtifactRef) {
        this.sentEvidenceArtifactRef = sentEvidenceArtifactRef;
    }
    
    public String getDeliveredEvidenceArtifactRef() {
        return deliveredEvidenceArtifactRef;
    }
    
    public void setDeliveredEvidenceArtifactRef(String deliveredEvidenceArtifactRef) {
        this.deliveredEvidenceArtifactRef = deliveredEvidenceArtifactRef;
    }
    
    public String getFailedEvidenceArtifactRef() {
        return failedEvidenceArtifactRef;
    }
    
    public void setFailedEvidenceArtifactRef(String failedEvidenceArtifactRef) {
        this.failedEvidenceArtifactRef = failedEvidenceArtifactRef;
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
}
