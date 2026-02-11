package com.regulyn.nominee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class NomineeVerificationRequirementId implements Serializable {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "verification_step", nullable = false, length = 64)
    private String verificationStep;

    public NomineeVerificationRequirementId() {
    }

    public NomineeVerificationRequirementId(UUID tenantId, String verificationStep) {
        this.tenantId = tenantId;
        this.verificationStep = verificationStep;
    }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getVerificationStep() { return verificationStep; }
    public void setVerificationStep(String verificationStep) { this.verificationStep = verificationStep; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NomineeVerificationRequirementId that = (NomineeVerificationRequirementId) o;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(verificationStep, that.verificationStep);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, verificationStep);
    }
}
