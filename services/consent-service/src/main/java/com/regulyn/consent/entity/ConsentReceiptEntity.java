package com.regulyn.consent.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consent_receipts", schema = "consent")
public class ConsentReceiptEntity {
    
    @Id
    @Column(name = "receipt_id")
    private UUID receiptId;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "data_principal_id", nullable = false)
    private UUID dataPrincipalId;
    
    @Column(name = "purpose", nullable = false)
    private String purpose;
    
    @Column(name = "source", nullable = false)
    private String source;
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Column(name = "notice_id", nullable = false)
    private UUID noticeId;
    
    @Column(name = "version_id", nullable = false)
    private UUID versionId;
    
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;
    
    @Column(name = "language", nullable = false)
    private String language;
    
    @Column(name = "content_hash", nullable = false)
    private String contentHash;
    
    @Column(name = "receipt_hash", nullable = false)
    private String receiptHash;
    
    @Column(name = "client_ref")
    private String clientRef;
    
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    
    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;
    
    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;
    
    @Column(name = "withdraw_evidence_id")
    private UUID withdrawEvidenceId;
    
    @PrePersist
    protected void onCreate() {
        if (receiptId == null) {
            receiptId = UUID.randomUUID();
        }
        if (status == null) {
            status = "GRANTED";
        }
        if (grantedAt == null) {
            grantedAt = Instant.now();
        }
    }

    // Getters and setters
    public UUID getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(UUID receiptId) {
        this.receiptId = receiptId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getDataPrincipalId() {
        return dataPrincipalId;
    }

    public void setDataPrincipalId(UUID dataPrincipalId) {
        this.dataPrincipalId = dataPrincipalId;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getNoticeId() {
        return noticeId;
    }

    public void setNoticeId(UUID noticeId) {
        this.noticeId = noticeId;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public void setVersionId(UUID versionId) {
        this.versionId = versionId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getReceiptHash() {
        return receiptHash;
    }

    public void setReceiptHash(String receiptHash) {
        this.receiptHash = receiptHash;
    }

    public String getClientRef() {
        return clientRef;
    }

    public void setClientRef(String clientRef) {
        this.clientRef = clientRef;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(Instant grantedAt) {
        this.grantedAt = grantedAt;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public void setWithdrawnAt(Instant withdrawnAt) {
        this.withdrawnAt = withdrawnAt;
    }

    public UUID getWithdrawEvidenceId() {
        return withdrawEvidenceId;
    }

    public void setWithdrawEvidenceId(UUID withdrawEvidenceId) {
        this.withdrawEvidenceId = withdrawEvidenceId;
    }
}
