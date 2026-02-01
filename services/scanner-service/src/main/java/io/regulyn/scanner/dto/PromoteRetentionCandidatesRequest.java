package io.regulyn.scanner.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class PromoteRetentionCandidatesRequest {

    private RiskLevel minRiskLevel = RiskLevel.MED;

    @NotNull(message = "subjectType is required")
    private SubjectType subjectType;

    private String entityType;

    @Min(value = 1, message = "limit must be between 1 and 500")
    @Max(value = 500, message = "limit must be between 1 and 500")
    private Integer limit = 100;

    // Enums
    public enum RiskLevel {
        LOW,
        MED,
        HIGH
    }

    public enum SubjectType {
        CUSTOMER,
        EMPLOYEE,
        VENDOR,
        OTHER
    }

    // Getters and Setters
    public RiskLevel getMinRiskLevel() {
        return minRiskLevel;
    }

    public void setMinRiskLevel(RiskLevel minRiskLevel) {
        this.minRiskLevel = minRiskLevel;
    }

    public SubjectType getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(SubjectType subjectType) {
        this.subjectType = subjectType;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
