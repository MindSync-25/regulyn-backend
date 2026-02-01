package com.regulyn.employee.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "employee_exports", schema = "employee")
public class EmployeeExport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "export_id")
    private UUID exportId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "bundle_id", nullable = false)
    private UUID bundleId;

    @Column(name = "evidence_export_id", nullable = false)
    private UUID evidenceExportId;

    @Column(name = "title")
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Getters and setters
    public UUID getExportId() {
        return exportId;
    }

    public void setExportId(UUID exportId) {
        this.exportId = exportId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public void setBundleId(UUID bundleId) {
        this.bundleId = bundleId;
    }

    public UUID getEvidenceExportId() {
        return evidenceExportId;
    }

    public void setEvidenceExportId(UUID evidenceExportId) {
        this.evidenceExportId = evidenceExportId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
