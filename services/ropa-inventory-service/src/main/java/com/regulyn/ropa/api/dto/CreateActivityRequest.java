package com.regulyn.ropa.api.dto;

import com.regulyn.ropa.model.RopaActivityVersion;
import jakarta.validation.constraints.*;

import java.util.Map;

public class CreateActivityRequest {

    @NotBlank(message = "activityName is required")
    private String activityName;

    @NotBlank(message = "purpose is required")
    private String purpose;

    @NotNull(message = "lawfulBasis is required")
    private RopaActivityVersion.LawfulBasis lawfulBasis;

    @NotNull(message = "dataPrincipalType is required")
    private RopaActivityVersion.DataPrincipalType dataPrincipalType;

    private String description;

    private String retentionPolicy;

    @Min(value = 0, message = "retentionDays must be at least 0")
    @Max(value = 36500, message = "retentionDays must not exceed 36500")
    private Integer retentionDays;

    @NotNull(message = "riskLevel is required")
    private RopaActivityVersion.RiskLevel riskLevel;

    private Boolean enabled = true;

    private Map<String, Object> metadata;

    // Getters and Setters
    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public RopaActivityVersion.LawfulBasis getLawfulBasis() {
        return lawfulBasis;
    }

    public void setLawfulBasis(RopaActivityVersion.LawfulBasis lawfulBasis) {
        this.lawfulBasis = lawfulBasis;
    }

    public RopaActivityVersion.DataPrincipalType getDataPrincipalType() {
        return dataPrincipalType;
    }

    public void setDataPrincipalType(RopaActivityVersion.DataPrincipalType dataPrincipalType) {
        this.dataPrincipalType = dataPrincipalType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRetentionPolicy() {
        return retentionPolicy;
    }

    public void setRetentionPolicy(String retentionPolicy) {
        this.retentionPolicy = retentionPolicy;
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }

    public RopaActivityVersion.RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RopaActivityVersion.RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
