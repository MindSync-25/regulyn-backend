package com.regulyn.ropa.api.dto;

import com.regulyn.ropa.model.RetentionBasis;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class RetentionPolicyUpsertRequest {

    @NotNull(message = "retentionDays is required")
    @Min(value = 1, message = "retentionDays must be greater than 0")
    private Integer retentionDays;

    @NotNull(message = "retentionBasis is required")
    private RetentionBasis retentionBasis;

    private String retentionNote;

    private Boolean reviewRequired = false;

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
