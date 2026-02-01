package com.regulyn.vendor.dto;

import java.time.Instant;
import java.util.UUID;

public class VendorExportResponse {
    private UUID exportId;
    private UUID bundleId;
    private UUID evidenceExportId;
    private Instant createdAt;
    private String downloadUrl;

    public UUID getExportId() { return exportId; }
    public void setExportId(UUID exportId) { this.exportId = exportId; }

    public UUID getBundleId() { return bundleId; }
    public void setBundleId(UUID bundleId) { this.bundleId = bundleId; }

    public UUID getEvidenceExportId() { return evidenceExportId; }
    public void setEvidenceExportId(UUID evidenceExportId) { this.evidenceExportId = evidenceExportId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
}
