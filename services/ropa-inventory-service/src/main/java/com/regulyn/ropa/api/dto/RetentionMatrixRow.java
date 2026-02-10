package com.regulyn.ropa.api.dto;

import com.regulyn.ropa.model.RetentionBasis;

import java.util.UUID;

public class RetentionMatrixRow {

    private UUID systemId;
    private UUID activityId;
    private UUID dataCategoryId;
    private UUID purposeVersionId;
    private RetentionEffectiveLevel effectiveLevel;
    private Integer retentionDays;
    private RetentionBasis retentionBasis;
    private String retentionNote;
    private Boolean reviewRequired;

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

    public RetentionEffectiveLevel getEffectiveLevel() {
        return effectiveLevel;
    }

    public void setEffectiveLevel(RetentionEffectiveLevel effectiveLevel) {
        this.effectiveLevel = effectiveLevel;
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }

    public RetentionBasis getRetentionBasis() {
        return retentionBasis;
    }

    public void setRetentionBasis(RetentionBasis retentionBasis) {
        this.retentionBasis = retentionBasis;
    }

    public String getRetentionNote() {
        return retentionNote;
    }

    public void setRetentionNote(String retentionNote) {
        this.retentionNote = retentionNote;
    }

    public Boolean getReviewRequired() {
        return reviewRequired;
    }

    public void setReviewRequired(Boolean reviewRequired) {
        this.reviewRequired = reviewRequired;
    }
}
