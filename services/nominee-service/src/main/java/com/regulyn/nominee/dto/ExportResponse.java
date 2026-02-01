package com.regulyn.nominee.dto;

import java.time.Instant;
import java.util.UUID;

public class ExportResponse {

    private UUID exportId;
    private UUID nomineeId;
    private String format;
    private String status;
    private String downloadUrl;
    private Instant requestedAt;
    private UUID requestedBy;
    private Instant completedAt;
    private Long fileSizeBytes;
    private Instant expiresAt;

    // Getters and Setters
    public UUID getExportId() {
        return exportId;
    }

    public void setExportId(UUID exportId) {
        this.exportId = exportId;
    }

    public UUID getNomineeId() {
        return nomineeId;
    }

    public void setNomineeId(UUID nomineeId) {
        this.nomineeId = nomineeId;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(UUID requestedBy) {
        this.requestedBy = requestedBy;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
