package io.regulyn.identity.dto;

public class TenantPlanLimitsRequest {
    private Integer maxUsers;
    private Integer dsarPerMonth;
    private Integer exportsPerMonth;

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
}
