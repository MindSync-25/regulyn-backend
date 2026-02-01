package com.regulyn.vendor.dto;

import com.regulyn.vendor.model.VendorAgreement;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class AgreementResponse {
    private UUID agreementId;
    private UUID vendorId;
    private VendorAgreement.AgreementType agreementType;
    private VendorAgreement.AgreementStatus status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String docRef;
    private String notes;
    private Instant createdAt;

    public UUID getAgreementId() { return agreementId; }
    public void setAgreementId(UUID agreementId) { this.agreementId = agreementId; }

    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }

    public VendorAgreement.AgreementType getAgreementType() { return agreementType; }
    public void setAgreementType(VendorAgreement.AgreementType agreementType) { this.agreementType = agreementType; }

    public VendorAgreement.AgreementStatus getStatus() { return status; }
    public void setStatus(VendorAgreement.AgreementStatus status) { this.status = status; }

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
