package com.regulyn.nominee.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class ExportNomineeRequest {

    @NotNull(message = "Nominee ID is required")
    private UUID nomineeId;

    @NotNull(message = "Export format is required")
    private ExportFormat format;

    private boolean includeClaimHistory;
    private boolean includeRightsGrants;
    private boolean includeDocuments;

    public enum ExportFormat {
        JSON,
        PDF,
        CSV
    }

    // Getters and Setters
    public UUID getNomineeId() {
        return nomineeId;
    }

    public void setNomineeId(UUID nomineeId) {
        this.nomineeId = nomineeId;
    }

    public ExportFormat getFormat() {
        return format;
    }

    public void setFormat(ExportFormat format) {
        this.format = format;
    }

    public boolean isIncludeClaimHistory() {
        return includeClaimHistory;
    }

    public void setIncludeClaimHistory(boolean includeClaimHistory) {
        this.includeClaimHistory = includeClaimHistory;
    }

    public boolean isIncludeRightsGrants() {
        return includeRightsGrants;
    }

    public void setIncludeRightsGrants(boolean includeRightsGrants) {
        this.includeRightsGrants = includeRightsGrants;
    }

    public boolean isIncludeDocuments() {
        return includeDocuments;
    }

    public void setIncludeDocuments(boolean includeDocuments) {
        this.includeDocuments = includeDocuments;
    }
}
