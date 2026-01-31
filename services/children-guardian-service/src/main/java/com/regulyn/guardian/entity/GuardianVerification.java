package com.regulyn.guardian.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "guardian_verifications", schema = "guardian")
public class GuardianVerification {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "child_profile_id", nullable = false)
    private UUID childProfileId;

    @Column(name = "guardian_id", nullable = false)
    private UUID guardianId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "evidence_artifact_ref")
    private String evidenceArtifactRef;

    @Column(name = "evidence_artifact_hash")
    private String evidenceArtifactHash;

    @Column(name = "metadata")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata = "{}";

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (submittedAt == null) {
            submittedAt = Instant.now();
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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

    public String getEvidenceArtifactRef() { return evidenceArtifactRef; }
    public void setEvidenceArtifactRef(String evidenceArtifactRef) { this.evidenceArtifactRef = evidenceArtifactRef; }

    public String getEvidenceArtifactHash() { return evidenceArtifactHash; }
    public void setEvidenceArtifactHash(String evidenceArtifactHash) { this.evidenceArtifactHash = evidenceArtifactHash; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
