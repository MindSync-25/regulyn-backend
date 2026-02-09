package io.regulyn.identity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_plan_limits")
public class TenantPlanLimits {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "max_users", nullable = false)
    private Integer maxUsers = 5;

    @Column(name = "dsar_per_month", nullable = false)
    private Integer dsarPerMonth = 50;

    @Column(name = "exports_per_month", nullable = false)
    private Integer exportsPerMonth = 50;

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

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public Integer getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(Integer maxUsers) {
        this.maxUsers = maxUsers;
    }

    public Integer getDsarPerMonth() {
        return dsarPerMonth;
    }

    public void setDsarPerMonth(Integer dsarPerMonth) {
        this.dsarPerMonth = dsarPerMonth;
    }

    public Integer getExportsPerMonth() {
        return exportsPerMonth;
    }

    public void setExportsPerMonth(Integer exportsPerMonth) {
        this.exportsPerMonth = exportsPerMonth;
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
