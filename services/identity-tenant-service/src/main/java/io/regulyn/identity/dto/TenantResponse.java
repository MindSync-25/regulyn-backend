package io.regulyn.identity.dto;

import java.time.Instant;
import java.util.UUID;

public class TenantResponse {
    private UUID tenantId;
    private String name;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant activatedAt;
    private Instant suspendedAt;
    private Instant deletedAt;
    private Instant adminBootstrappedAt;
    private UUID adminBootstrapUserId;
    private Instant deleteRequestedAt;
    private Boolean complianceHold;
    private Boolean readOnly;
    private String planCode;

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

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(Instant activatedAt) {
        this.activatedAt = activatedAt;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public void setSuspendedAt(Instant suspendedAt) {
        this.suspendedAt = suspendedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Instant getAdminBootstrappedAt() {
        return adminBootstrappedAt;
    }

    public void setAdminBootstrappedAt(Instant adminBootstrappedAt) {
        this.adminBootstrappedAt = adminBootstrappedAt;
    }

    public UUID getAdminBootstrapUserId() {
        return adminBootstrapUserId;
    }

    public void setAdminBootstrapUserId(UUID adminBootstrapUserId) {
        this.adminBootstrapUserId = adminBootstrapUserId;
    }

    public Instant getDeleteRequestedAt() {
        return deleteRequestedAt;
    }

    public void setDeleteRequestedAt(Instant deleteRequestedAt) {
        this.deleteRequestedAt = deleteRequestedAt;
    }

    public Boolean getComplianceHold() {
        return complianceHold;
    }

    public void setComplianceHold(Boolean complianceHold) {
        this.complianceHold = complianceHold;
    }

    public Boolean getReadOnly() {
        return readOnly;
    }

    public void setReadOnly(Boolean readOnly) {
        this.readOnly = readOnly;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }
}
