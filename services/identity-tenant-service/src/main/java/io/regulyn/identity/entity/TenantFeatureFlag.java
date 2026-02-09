package io.regulyn.identity.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_feature_flags", indexes = {
    @Index(name = "idx_tff_tenant_id", columnList = "tenant_id")
})
public class TenantFeatureFlag {

    @Id
    @Column(name = "feature_flag_id", nullable = false, updatable = false)
    private UUID featureFlagId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "flag_key", nullable = false, length = 120)
    private String flagKey;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value_json", columnDefinition = "jsonb")
    private String valueJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getFeatureFlagId() {
        return featureFlagId;
    }

    public void setFeatureFlagId(UUID featureFlagId) {
        this.featureFlagId = featureFlagId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getFlagKey() {
        return flagKey;
    }

    public void setFlagKey(String flagKey) {
        this.flagKey = flagKey;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getValueJson() {
        return valueJson;
    }

    public void setValueJson(String valueJson) {
        this.valueJson = valueJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
