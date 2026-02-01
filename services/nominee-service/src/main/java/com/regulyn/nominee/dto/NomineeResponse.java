package com.regulyn.nominee.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class NomineeResponse {

    private UUID nomineeId;
    private UUID dataPrincipalId;
    private String nomineeName;
    private String nomineeEmail;
    private String nomineePhone;
    private String relationship;
    private String scope;
    private String status;
    private Boolean requiresVerification;
    private Instant verifiedAt;
    private UUID verifiedBy;
    private LocalDate validFrom;
    private LocalDate validTo;
    private Instant createdAt;
    private Instant updatedAt;

    // Getters and Setters
    public UUID getNomineeId() {
        return nomineeId;
    }

    public void setNomineeId(UUID nomineeId) {
        this.nomineeId = nomineeId;
    }

    public UUID getDataPrincipalId() {
        return dataPrincipalId;
    }

    public void setDataPrincipalId(UUID dataPrincipalId) {
        this.dataPrincipalId = dataPrincipalId;
    }

    public String getNomineeName() {
        return nomineeName;
    }

    public void setNomineeName(String nomineeName) {
        this.nomineeName = nomineeName;
    }

    public String getNomineeEmail() {
        return nomineeEmail;
    }

    public void setNomineeEmail(String nomineeEmail) {
        this.nomineeEmail = nomineeEmail;
    }

    public String getNomineePhone() {
        return nomineePhone;
    }

    public void setNomineePhone(String nomineePhone) {
        this.nomineePhone = nomineePhone;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getRequiresVerification() {
        return requiresVerification;
    }

    public void setRequiresVerification(Boolean requiresVerification) {
        this.requiresVerification = requiresVerification;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public UUID getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(UUID verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
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
