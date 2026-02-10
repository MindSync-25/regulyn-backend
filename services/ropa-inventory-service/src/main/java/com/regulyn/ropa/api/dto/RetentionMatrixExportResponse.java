package com.regulyn.ropa.api.dto;

import com.regulyn.ropa.model.ReportStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class RetentionMatrixExportResponse {

    private UUID reportExportId;
    private ReportStatus status;
    private List<RetentionMatrixRow> rows;
    private Instant generatedAt;
    private String artifactRef;
    private String artifactHash;

    public UUID getReportExportId() {
        return reportExportId;
    }

    public void setReportExportId(UUID reportExportId) {
        this.reportExportId = reportExportId;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public List<RetentionMatrixRow> getRows() {
        return rows;
    }

    public void setRows(List<RetentionMatrixRow> rows) {
        this.rows = rows;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getArtifactRef() {
        return artifactRef;
    }

    public void setArtifactRef(String artifactRef) {
        this.artifactRef = artifactRef;
    }

    public String getArtifactHash() {
        return artifactHash;
    }

    public void setArtifactHash(String artifactHash) {
        this.artifactHash = artifactHash;
    }
}
