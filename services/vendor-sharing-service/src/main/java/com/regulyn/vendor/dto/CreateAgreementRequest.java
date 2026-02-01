package com.regulyn.vendor.dto;

import com.regulyn.vendor.model.VendorAgreement;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class CreateAgreementRequest {

    @NotNull(message = "agreementType is required")
    private VendorAgreement.AgreementType agreementType;

    @NotNull(message = "status is required")
    private VendorAgreement.AgreementStatus status;

    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String docRef;
    private String notes;

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
}
