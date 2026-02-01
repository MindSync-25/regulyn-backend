package com.regulyn.nominee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.UUID;

public class RegisterNomineeRequest {

    @NotNull(message = "dataPrincipalId is required")
    private UUID dataPrincipalId;

    @NotBlank(message = "nomineeName is required")
    private String nomineeName;

    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "Invalid email format")
    private String nomineeEmail;

    private String nomineePhone;

    @NotNull(message = "relationship is required")
    private Relationship relationship;

    @NotNull(message = "scope is required")
    private Scope scope;

    private LocalDate validFrom;

    private LocalDate validTo;

    private Boolean requiresVerification = true;

    public enum Relationship {
        FAMILY, LEGAL_REP, GUARDIAN, OTHER
    }

    public enum Scope {
        DSAR_ONLY, FULL_RIGHTS, LIMITED
    }

    // Getters and Setters
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

    public Relationship getRelationship() {
        return relationship;
    }

    public void setRelationship(Relationship relationship) {
        this.relationship = relationship;
    }

    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
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

    public Boolean getRequiresVerification() {
        return requiresVerification;
    }

    public void setRequiresVerification(Boolean requiresVerification) {
        this.requiresVerification = requiresVerification;
    }
}
