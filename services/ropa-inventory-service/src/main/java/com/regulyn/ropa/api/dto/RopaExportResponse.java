package com.regulyn.ropa.api.dto;

import java.util.UUID;

public class RopaExportResponse {

    private UUID bundleId;
    private UUID exportId;
    private String downloadPath;

    public RopaExportResponse() {
    }

    public RopaExportResponse(UUID bundleId, UUID exportId, String downloadPath) {
        this.bundleId = bundleId;
        this.exportId = exportId;
        this.downloadPath = downloadPath;
    }

    // Getters and Setters
    public UUID getBundleId() {
        return bundleId;
    }

    public void setBundleId(UUID bundleId) {
        this.bundleId = bundleId;
    }

    public UUID getExportId() {
        return exportId;
    }

    public void setExportId(UUID exportId) {
        this.exportId = exportId;
    }

    public String getDownloadPath() {
        return downloadPath;
    }

    public void setDownloadPath(String downloadPath) {
        this.downloadPath = downloadPath;
    }
}
