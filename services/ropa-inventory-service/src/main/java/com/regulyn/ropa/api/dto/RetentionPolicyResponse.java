package com.regulyn.ropa.api.dto;

import com.regulyn.ropa.model.RetentionBasis;

import java.time.Instant;
import java.util.UUID;

public class RetentionPolicyResponse {

    private String scope;
    private UUID systemId;
    private UUID activityId;
    private UUID dataCategoryId;
    private UUID purposeVersionId;
    private Integer retentionDays;
    private RetentionBasis retentionBasis;
    private String retentionNote;
    private Boolean reviewRequired;
    private Boolean created;
    private Instant updatedAt;

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

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

    public Boolean getCreated() {
        return created;
    }

    public void setCreated(Boolean created) {
        this.created = created;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
