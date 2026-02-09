package io.regulyn.identity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_monthly_usage", indexes = {
    @Index(name = "idx_tmu_tenant_month", columnList = "tenant_id, year_month")
})
public class TenantMonthlyUsage {

    @Id
    @Column(name = "usage_id", nullable = false, updatable = false)
    private UUID usageId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "year_month", nullable = false)
    private Integer yearMonth;

    @Column(name = "dsar_count", nullable = false)
    private Integer dsarCount = 0;

    @Column(name = "export_count", nullable = false)
    private Integer exportCount = 0;

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

    public UUID getUsageId() {
        return usageId;
    }

    public void setUsageId(UUID usageId) {
        this.usageId = usageId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public Integer getYearMonth() {
        return yearMonth;
    }

    public void setYearMonth(Integer yearMonth) {
        this.yearMonth = yearMonth;
    }

    public Integer getDsarCount() {
        return dsarCount;
    }

    public void setDsarCount(Integer dsarCount) {
        this.dsarCount = dsarCount;
    }

    public Integer getExportCount() {
        return exportCount;
    }

    public void setExportCount(Integer exportCount) {
        this.exportCount = exportCount;
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
