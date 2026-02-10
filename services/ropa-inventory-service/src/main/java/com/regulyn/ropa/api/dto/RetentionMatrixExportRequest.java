package com.regulyn.ropa.api.dto;

import com.regulyn.ropa.model.RopaActivityVersion;

import java.util.List;
import java.util.UUID;

public class RetentionMatrixExportRequest {

    private List<UUID> systemIds;
    private RopaActivityVersion.Status activityStatus = RopaActivityVersion.Status.PUBLISHED;
    private Boolean includeDisabled = false;

    public List<UUID> getSystemIds() {
        return systemIds;
    }

    public void setSystemIds(List<UUID> systemIds) {
        this.systemIds = systemIds;
    }

    public RopaActivityVersion.Status getActivityStatus() {
        return activityStatus;
    }

    public void setActivityStatus(RopaActivityVersion.Status activityStatus) {
        this.activityStatus = activityStatus;
    }

    public Boolean getIncludeDisabled() {
        return includeDisabled;
    }

    public void setIncludeDisabled(Boolean includeDisabled) {
        this.includeDisabled = includeDisabled;
    }
}
