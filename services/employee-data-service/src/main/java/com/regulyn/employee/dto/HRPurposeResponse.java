package com.regulyn.employee.dto;

import com.regulyn.employee.model.HRPurpose;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class HRPurposeResponse {

    private UUID hrPurposeId;
    private UUID tenantId;
    private HRPurpose.PurposeKey purposeKey;
    private String description;
    private HRPurpose.LawfulBasis lawfulBasis;
    private Integer retentionDays;
    private Boolean sensitive;
    private Map<String, Object> metadata;
    private Instant createdAt;

    public UUID getHrPurposeId() {
        return hrPurposeId;
    }

    public void setHrPurposeId(UUID hrPurposeId) {
        this.hrPurposeId = hrPurposeId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
