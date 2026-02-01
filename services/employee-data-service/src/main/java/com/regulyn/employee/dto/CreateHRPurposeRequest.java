package com.regulyn.employee.dto;

import com.regulyn.employee.model.HRPurpose;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.Map;

public class CreateHRPurposeRequest {

    @NotNull(message = "Purpose key is required")
    private HRPurpose.PurposeKey purposeKey;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Lawful basis is required")
    private HRPurpose.LawfulBasis lawfulBasis;

    @Min(value = 0, message = "Retention days must be non-negative")
    private Integer retentionDays;

    private Boolean sensitive = false;

    private Map<String, Object> metadata = new HashMap<>();

    public HRPurpose.PurposeKey getPurposeKey() {
        return purposeKey;
    }

    public void setPurposeKey(HRPurpose.PurposeKey purposeKey) {
        this.purposeKey = purposeKey;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public HRPurpose.LawfulBasis getLawfulBasis() {
        return lawfulBasis;
    }

    public void setLawfulBasis(HRPurpose.LawfulBasis lawfulBasis) {
        this.lawfulBasis = lawfulBasis;
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }

    public Boolean getSensitive() {
        return sensitive;
    }

    public void setSensitive(Boolean sensitive) {
        this.sensitive = sensitive;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
