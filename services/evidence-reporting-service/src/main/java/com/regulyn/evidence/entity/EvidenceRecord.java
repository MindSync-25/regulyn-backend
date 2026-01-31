package com.regulyn.evidence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evidence_records", schema = "evidence", indexes = {
    @Index(name = "idx_evidence_tenant", columnList = "tenant_id"),
    @Index(name = "idx_evidence_id", columnList = "evidence_id"),
    @Index(name = "idx_evidence_created", columnList = "created_at")
})
public class EvidenceRecord {

    @Id
    @Column(name = "evidence_pk")
    private UUID evidencePk;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "evidence_id", nullable = false, unique = true)
    private String evidenceId;

    @Column(name = "evidence_type", nullable = false)
    private String evidenceType;

    @Column(name = "evidence_hash", nullable = false)
    private String evidenceHash;

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @PrePersist
    protected void onCreate() {
        if (evidencePk == null) {
            evidencePk = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Getters and setters
    public UUID getEvidencePk() {
        return evidencePk;
    }

    public void setEvidencePk(UUID evidencePk) {
        this.evidencePk = evidencePk;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(String evidenceId) {
        this.evidenceId = evidenceId;
    }

    public String getEvidenceType() {
        return evidenceType;
    }

    public void setEvidenceType(String evidenceType) {
        this.evidenceType = evidenceType;
    }

    public String getEvidenceHash() {
        return evidenceHash;
    }

    public void setEvidenceHash(String evidenceHash) {
        this.evidenceHash = evidenceHash;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
}
