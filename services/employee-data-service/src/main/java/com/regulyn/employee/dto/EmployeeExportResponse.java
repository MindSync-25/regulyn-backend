package com.regulyn.employee.dto;

import java.time.Instant;
import java.util.UUID;

public class EmployeeExportResponse {

    private UUID exportId;
    private UUID bundleId;
    private UUID evidenceExportId;
    private String downloadPath;
    private Instant createdAt;

    public UUID getExportId() {
        return exportId;
    }

    public void setExportId(UUID exportId) {
        this.exportId = exportId;
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

    public String getDownloadPath() {
        return downloadPath;
    }

    public void setDownloadPath(String downloadPath) {
        this.downloadPath = downloadPath;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
