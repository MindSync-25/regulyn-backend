package io.regulyn.identity.dto;

public class TenantUsageIncrementResponse {
    private Integer yearMonth;
    private Integer dsarCount;
    private Integer exportCount;
    private Boolean readOnly;
    private String readOnlyReason;

    public Integer getYearMonth() {
        return yearMonth;
    }

    public void setYearMonth(Integer yearMonth) {
        this.yearMonth = yearMonth;
    }

    public Integer getDsarCount() {
        return dsarCount;
    }

    public void setDsarCount(Integer dsarCount) {
        this.dsarCount = dsarCount;
    }

    public Integer getExportCount() {
        return exportCount;
    }

    public void setExportCount(Integer exportCount) {
        this.exportCount = exportCount;
    }

    public Boolean getReadOnly() {
        return readOnly;
    }

    public void setReadOnly(Boolean readOnly) {
        this.readOnly = readOnly;
    }

    public String getReadOnlyReason() {
        return readOnlyReason;
    }

    public void setReadOnlyReason(String readOnlyReason) {
        this.readOnlyReason = readOnlyReason;
    }
}