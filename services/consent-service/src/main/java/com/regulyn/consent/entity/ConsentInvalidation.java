package com.regulyn.consent.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consent_invalidations", schema = "consent")
public class ConsentInvalidation {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "data_principal_id", nullable = false)
    private UUID dataPrincipalId;

    @Column(name = "consent_receipt_id", nullable = false)
    private UUID consentReceiptId;

    @Column(name = "invalidation_reason", nullable = false, length = 120)
    private String invalidationReason;

    @Column(name = "invalidated_at", nullable = false)
    private Instant invalidatedAt;

    @Column(name = "triggered_by_purpose_version_id")
    private UUID triggeredByPurposeVersionId;

    @Column(name = "audit_event_id")
    private UUID auditEventId;

    @Column(name = "outbox_event_id")
    private UUID outboxEventId;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (invalidatedAt == null) {
            invalidatedAt = Instant.now();
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

    public UUID getConsentReceiptId() {
        return consentReceiptId;
    }

    public void setConsentReceiptId(UUID consentReceiptId) {
        this.consentReceiptId = consentReceiptId;
    }

    public String getInvalidationReason() {
        return invalidationReason;
    }

    public void setInvalidationReason(String invalidationReason) {
        this.invalidationReason = invalidationReason;
    }

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }

    public void setInvalidatedAt(Instant invalidatedAt) {
        this.invalidatedAt = invalidatedAt;
    }

    public UUID getTriggeredByPurposeVersionId() {
        return triggeredByPurposeVersionId;
    }

    public void setTriggeredByPurposeVersionId(UUID triggeredByPurposeVersionId) {
        this.triggeredByPurposeVersionId = triggeredByPurposeVersionId;
    }

    public UUID getAuditEventId() {
        return auditEventId;
    }

    public void setAuditEventId(UUID auditEventId) {
        this.auditEventId = auditEventId;
    }

    public UUID getOutboxEventId() {
        return outboxEventId;
    }

    public void setOutboxEventId(UUID outboxEventId) {
        this.outboxEventId = outboxEventId;
    }
}
