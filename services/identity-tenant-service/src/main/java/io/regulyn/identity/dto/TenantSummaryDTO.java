package io.regulyn.identity.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Privacy-safe tenant summary for platform console listing.
 * Contains only non-PII metadata.
 */
public class TenantSummaryDTO {
    private UUID tenantId;
    private String name;
    private String status;
    private String planCode;
    private Instant createdAt;

    public TenantSummaryDTO() {
    }

    public TenantSummaryDTO(UUID tenantId, String name, String status, String planCode, Instant createdAt) {
        this.tenantId = tenantId;
        this.name = name;
        this.status = status;
        this.planCode = planCode;
        this.createdAt = createdAt;
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
}
