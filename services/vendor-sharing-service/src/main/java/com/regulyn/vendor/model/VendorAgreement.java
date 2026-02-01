package com.regulyn.vendor.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "vendor_agreements", schema = "vendor")
public class VendorAgreement {

    public enum AgreementType {
        DPA, MSA, SCC, NDA, OTHER
    }

    public enum AgreementStatus {
        DRAFT, ACTIVE, EXPIRED
    }

    @Id
    @Column(name = "agreement_id")
    private UUID agreementId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_type", nullable = false)
    private AgreementType agreementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AgreementStatus status;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "doc_ref")
    private String docRef;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (agreementId == null) {
            agreementId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Getters and setters
    public UUID getAgreementId() { return agreementId; }
    public void setAgreementId(UUID agreementId) { this.agreementId = agreementId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }

    public AgreementType getAgreementType() { return agreementType; }
    public void setAgreementType(AgreementType agreementType) { this.agreementType = agreementType; }

    public AgreementStatus getStatus() { return status; }
    public void setStatus(AgreementStatus status) { this.status = status; }

    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }

    public String getDocRef() { return docRef; }
    public void setDocRef(String docRef) { this.docRef = docRef; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
