package io.regulyn.scanner.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scan_run_evidence_refs", schema = "scanner")
public class ScanRunEvidenceRefEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "bundle_artifact_ref", nullable = false)
    private String bundleArtifactRef;

    @Column(name = "bundle_hash", nullable = false, length = 64)
    private String bundleHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public String getBundleArtifactRef() {
        return bundleArtifactRef;
    }

    public void setBundleArtifactRef(String bundleArtifactRef) {
        this.bundleArtifactRef = bundleArtifactRef;
    }

    public String getBundleHash() {
        return bundleHash;
    }

    public void setBundleHash(String bundleHash) {
        this.bundleHash = bundleHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}