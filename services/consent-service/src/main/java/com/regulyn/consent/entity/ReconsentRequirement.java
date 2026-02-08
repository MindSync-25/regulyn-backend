package com.regulyn.consent.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconsent_requirements", schema = "consent")
public class ReconsentRequirement {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "data_principal_id", nullable = false)
    private UUID dataPrincipalId;

    @Column(name = "notice_id", nullable = false)
    private UUID noticeId;

    @Column(name = "purpose_key", nullable = false, length = 200)
    private String purposeKey;

    @Column(name = "required_purpose_version_id", nullable = false)
    private UUID requiredPurposeVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReconsentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "satisfied_at")
    private Instant satisfiedAt;

    @Column(name = "satisfied_by_consent_receipt_id")
    private UUID satisfiedByConsentReceiptId;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = ReconsentStatus.REQUIRED;
        }
    }

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

    public UUID getDataPrincipalId() {
        return dataPrincipalId;
    }

    public void setDataPrincipalId(UUID dataPrincipalId) {
        this.dataPrincipalId = dataPrincipalId;
    }

    public UUID getNoticeId() {
        return noticeId;
    }

    public void setNoticeId(UUID noticeId) {
        this.noticeId = noticeId;
    }

    public String getPurposeKey() {
        return purposeKey;
    }

    public void setPurposeKey(String purposeKey) {
        this.purposeKey = purposeKey;
    }

    public UUID getRequiredPurposeVersionId() {
        return requiredPurposeVersionId;
    }

    public void setRequiredPurposeVersionId(UUID requiredPurposeVersionId) {
        this.requiredPurposeVersionId = requiredPurposeVersionId;
    }

    public ReconsentStatus getStatus() {
        return status;
    }

    public void setStatus(ReconsentStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getSatisfiedAt() {
        return satisfiedAt;
    }

    public void setSatisfiedAt(Instant satisfiedAt) {
        this.satisfiedAt = satisfiedAt;
    }

    public UUID getSatisfiedByConsentReceiptId() {
        return satisfiedByConsentReceiptId;
    }

    public void setSatisfiedByConsentReceiptId(UUID satisfiedByConsentReceiptId) {
        this.satisfiedByConsentReceiptId = satisfiedByConsentReceiptId;
    }
}
