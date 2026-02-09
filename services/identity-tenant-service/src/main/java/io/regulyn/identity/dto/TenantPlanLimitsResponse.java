package io.regulyn.identity.dto;

public class TenantPlanLimitsResponse {
    private Integer maxUsers;
    private Integer dsarPerMonth;
    private Integer exportsPerMonth;
    private Integer yearMonth;
    private Integer dsarCount;
    private Integer exportCount;
    private Long enabledUsers;
    private Boolean readOnly;
    private String readOnlyReason;

    public Integer getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(Integer maxUsers) {
        this.maxUsers = maxUsers;
    }

    public Integer getDsarPerMonth() {
        return dsarPerMonth;
    }

    public void setDsarPerMonth(Integer dsarPerMonth) {
        this.dsarPerMonth = dsarPerMonth;
    }

    public Integer getExportsPerMonth() {
        return exportsPerMonth;
    }

    public void setExportsPerMonth(Integer exportsPerMonth) {
        this.exportsPerMonth = exportsPerMonth;
    }

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

    public Long getEnabledUsers() {
        return enabledUsers;
    }

    public void setEnabledUsers(Long enabledUsers) {
        this.enabledUsers = enabledUsers;
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
