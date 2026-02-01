package com.regulyn.vendor.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vendor_exports", schema = "vendor")
public class VendorExport {

    @Id
    @Column(name = "export_id")
    private UUID exportId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "bundle_id", nullable = false)
    private UUID bundleId;

    @Column(name = "evidence_export_id", nullable = false)
    private UUID evidenceExportId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (exportId == null) {
            exportId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Getters and setters
    public UUID getExportId() { return exportId; }
    public void setExportId(UUID exportId) { this.exportId = exportId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getBundleId() { return bundleId; }
    public void setBundleId(UUID bundleId) { this.bundleId = bundleId; }

    public UUID getEvidenceExportId() { return evidenceExportId; }
    public void setEvidenceExportId(UUID evidenceExportId) { this.evidenceExportId = evidenceExportId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
