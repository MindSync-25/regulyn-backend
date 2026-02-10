package com.regulyn.ropa.api.dto;

import java.util.UUID;

public class RetentionResolveResponse {

    private UUID systemId;
    private UUID activityId;
    private UUID dataCategoryId;
    private UUID purposeVersionId;
    private RetentionEffectiveRetention effective;

    public UUID getSystemId() {
        return systemId;
    }

    public void setSystemId(UUID systemId) {
        this.systemId = systemId;
    }

    public UUID getActivityId() {
        return activityId;
    }

    public void setActivityId(UUID activityId) {
        this.activityId = activityId;
    }

    public UUID getDataCategoryId() {
        return dataCategoryId;
    }

    public void setDataCategoryId(UUID dataCategoryId) {
        this.dataCategoryId = dataCategoryId;
    }

    public UUID getPurposeVersionId() {
        return purposeVersionId;
    }

    public void setPurposeVersionId(UUID purposeVersionId) {
        this.purposeVersionId = purposeVersionId;
    }

    public RetentionEffectiveRetention getEffective() {
        return effective;
    }

    public void setEffective(RetentionEffectiveRetention effective) {
        this.effective = effective;
    }
}
