package com.regulyn.guardian.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "age_threshold_rules", schema = "children")
public class AgeThresholdRuleEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "region_country_code", nullable = false, length = 2)
    private String regionCountryCode;

    @Column(name = "region_state_code", length = 10)
    private String regionStateCode;

    @Column(name = "threshold_age_years", nullable = false)
    private Short thresholdAgeYears;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "min_legal_age_years", nullable = false)
    private Short minLegalAgeYears;

    @Column(name = "max_legal_age_years", nullable = false)
    private Short maxLegalAgeYears;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getRegionCountryCode() {
        return regionCountryCode;
    }

    public void setRegionCountryCode(String regionCountryCode) {
        this.regionCountryCode = regionCountryCode;
    }

    public String getRegionStateCode() {
        return regionStateCode;
    }

    public void setRegionStateCode(String regionStateCode) {
        this.regionStateCode = regionStateCode;
    }

    public Short getThresholdAgeYears() {
        return thresholdAgeYears;
    }

    public void setThresholdAgeYears(Short thresholdAgeYears) {
        this.thresholdAgeYears = thresholdAgeYears;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public Short getMinLegalAgeYears() {
        return minLegalAgeYears;
    }

    public void setMinLegalAgeYears(Short minLegalAgeYears) {
        this.minLegalAgeYears = minLegalAgeYears;
    }

    public Short getMaxLegalAgeYears() {
        return maxLegalAgeYears;
    }

    public void setMaxLegalAgeYears(Short maxLegalAgeYears) {
        this.maxLegalAgeYears = maxLegalAgeYears;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }
}
