package com.regulyn.guardian.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "children_exports", schema = "children")
public class ChildrenExport {
    
    @Id
    @Column(name = "export_id")
    private UUID exportId;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "bundle_id", nullable = false)
    private UUID bundleId;
    
    @Column(name = "evidence_export_id")
    private UUID evidenceExportId;
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    // Constructors
    public ChildrenExport() {
        this.exportId = UUID.randomUUID();
        this.createdAt = Instant.now();
    }
    
    // Getters and Setters
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
