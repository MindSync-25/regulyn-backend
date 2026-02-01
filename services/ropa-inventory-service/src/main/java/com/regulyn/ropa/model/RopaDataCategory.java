package com.regulyn.ropa.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ropa_data_categories", schema = "ropa")
public class RopaDataCategory {

    @Id
    @Column(name = "data_category_id")
    private UUID dataCategoryId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_key", nullable = false)
    private CategoryKey categoryKey;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "sensitive", nullable = false)
    private Boolean sensitive;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (dataCategoryId == null) {
            dataCategoryId = UUID.randomUUID();
        }
        createdAt = Instant.now();
        if (metadata == null) {
            metadata = new HashMap<>();
        }
    }

    // Getters and Setters
    public UUID getDataCategoryId() {
        return dataCategoryId;
    }

    public void setDataCategoryId(UUID dataCategoryId) {
        this.dataCategoryId = dataCategoryId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public CategoryKey getCategoryKey() {
        return categoryKey;
    }

    public void setCategoryKey(CategoryKey categoryKey) {
        this.categoryKey = categoryKey;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
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

    public enum CategoryKey {
        PII, FINANCIAL, HEALTH, BIOMETRIC, CHILD_DATA, EMPLOYEE_DATA, DEVICE, OTHER
    }
}
