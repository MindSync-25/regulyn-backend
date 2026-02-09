package com.regulyn.vendor.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class CreateVendorAccessExportResponse {

    private UUID exportId;
    private String status;
    private String artifactRef;
    private String fileHash;
    private VendorAccessExportCountsDto counts;
    private OffsetDateTime requestedAt;
    private OffsetDateTime rangeStart;
    private OffsetDateTime rangeEnd;

    public UUID getExportId() {
        return exportId;
    }

    public void setExportId(UUID exportId) {
        this.exportId = exportId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getArtifactRef() {
        return artifactRef;
    }

    public void setArtifactRef(String artifactRef) {
        this.artifactRef = artifactRef;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public VendorAccessExportCountsDto getCounts() {
        return counts;
    }

    public void setCounts(VendorAccessExportCountsDto counts) {
        this.counts = counts;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(OffsetDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public OffsetDateTime getRangeStart() {
        return rangeStart;
    }

    public void setRangeStart(OffsetDateTime rangeStart) {
        this.rangeStart = rangeStart;
    }

    public OffsetDateTime getRangeEnd() {
        return rangeEnd;
    }

    public void setRangeEnd(OffsetDateTime rangeEnd) {
        this.rangeEnd = rangeEnd;
    }
}
