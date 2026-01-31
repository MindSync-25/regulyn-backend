package com.regulyn.guardian.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "guardian_consents", schema = "guardian")
public class GuardianConsent {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "child_profile_id", nullable = false)
    private UUID childProfileId;

    @Column(name = "guardian_id", nullable = false)
    private UUID guardianId;

    @Column(name = "consent_receipt_id")
    private UUID consentReceiptId;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;

    @Column(name = "signed_artifact_ref")
    private String signedArtifactRef;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "metadata")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata = "{}";

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (signedAt == null) {
            signedAt = Instant.now();
        }
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getChildProfileId() { return childProfileId; }
    public void setChildProfileId(UUID childProfileId) { this.childProfileId = childProfileId; }

    public UUID getGuardianId() { return guardianId; }
    public void setGuardianId(UUID guardianId) { this.guardianId = guardianId; }

    public UUID getConsentReceiptId() { return consentReceiptId; }
    public void setConsentReceiptId(UUID consentReceiptId) { this.consentReceiptId = consentReceiptId; }

    public Instant getSignedAt() { return signedAt; }
    public void setSignedAt(Instant signedAt) { this.signedAt = signedAt; }

    public String getSignedArtifactRef() { return signedArtifactRef; }
    public void setSignedArtifactRef(String signedArtifactRef) { this.signedArtifactRef = signedArtifactRef; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getWithdrawnAt() { return withdrawnAt; }
    public void setWithdrawnAt(Instant withdrawnAt) { this.withdrawnAt = withdrawnAt; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
