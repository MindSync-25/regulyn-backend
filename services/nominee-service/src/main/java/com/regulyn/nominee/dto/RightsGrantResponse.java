package com.regulyn.nominee.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class RightsGrantResponse {

    private UUID grantId;
    private UUID dataPrincipalId;
    private UUID nomineeId;
    private String scope;
    private String status;
    private Instant grantedAt;
    private UUID grantedBy;
    private LocalDate validFrom;
    private LocalDate validTo;
    private UUID claimId;
    private Instant revokedAt;
    private UUID revokedBy;

    // Getters and Setters
    public UUID getGrantId() {
        return grantId;
    }

    public void setGrantId(UUID grantId) {
        this.grantId = grantId;
    }

    public UUID getDataPrincipalId() {
        return dataPrincipalId;
    }

    public void setDataPrincipalId(UUID dataPrincipalId) {
        this.dataPrincipalId = dataPrincipalId;
    }

    public UUID getNomineeId() {
        return nomineeId;
    }

    public void setNomineeId(UUID nomineeId) {
        this.nomineeId = nomineeId;
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

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(Instant grantedAt) {
        this.grantedAt = grantedAt;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(UUID grantedBy) {
        this.grantedBy = grantedBy;
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

    public UUID getClaimId() {
        return claimId;
    }

    public void setClaimId(UUID claimId) {
        this.claimId = claimId;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public UUID getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(UUID revokedBy) {
        this.revokedBy = revokedBy;
    }
}
