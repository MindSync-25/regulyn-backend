package com.regulyn.consent.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consent_records", schema = "consent", indexes = {
    @Index(name = "idx_consent_tenant_user", columnList = "tenant_id,user_id"),
    @Index(name = "idx_consent_receipt", columnList = "receipt_id"),
    @Index(name = "idx_consent_created", columnList = "created_at")
})
public class ConsentRecord {

    @Id
    @Column(name = "consent_id")
    private UUID consentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "receipt_id", nullable = false, unique = true)
    private String receiptId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "purpose", nullable = false)
    private String purpose;

    @Column(name = "language", nullable = false)
    private String language;

    @Column(name = "notice_hash", nullable = false)
    private String noticeHash;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "payload_hash", nullable = false)
    private String payloadHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @PrePersist
    protected void onCreate() {
        if (consentId == null) {
            consentId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Getters and setters
    public UUID getConsentId() {
        return consentId;
    }

    public void setConsentId(UUID consentId) {
        this.consentId = consentId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(String receiptId) {
        this.receiptId = receiptId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getNoticeHash() {
        return noticeHash;
    }

    public void setNoticeHash(String noticeHash) {
        this.noticeHash = noticeHash;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
}
