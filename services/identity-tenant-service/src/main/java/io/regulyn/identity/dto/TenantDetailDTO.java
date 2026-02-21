package io.regulyn.identity.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Privacy-safe tenant details for platform console.
 * Contains configuration but NO user PII.
 */
public class TenantDetailDTO {
    private UUID tenantId;
    private String name;
    private String status;
    private String planCode;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant adminBootstrappedAt;
    private boolean hasAdminBootstrap;

    public TenantDetailDTO() {
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
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

    public Instant getAdminBootstrappedAt() {
        return adminBootstrappedAt;
    }

    public void setAdminBootstrappedAt(Instant adminBootstrappedAt) {
        this.adminBootstrappedAt = adminBootstrappedAt;
    }

    public boolean isHasAdminBootstrap() {
        return hasAdminBootstrap;
    }

    public void setHasAdminBootstrap(boolean hasAdminBootstrap) {
        this.hasAdminBootstrap = hasAdminBootstrap;
    }
}
