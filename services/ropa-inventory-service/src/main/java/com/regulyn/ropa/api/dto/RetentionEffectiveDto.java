package com.regulyn.ropa.api.dto;

import com.regulyn.ropa.model.RetentionBasis;

public class RetentionEffectiveDto {

    public enum Level {
        CATEGORY_PURPOSE,
        ACTIVITY,
        SYSTEM,
        NONE
    }

    private Level level;
    private Integer retentionDays;
    private RetentionBasis retentionBasis;
    private String retentionNote;
    private Boolean reviewRequired;

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
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
