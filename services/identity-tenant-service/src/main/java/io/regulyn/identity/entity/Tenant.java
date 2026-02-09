package io.regulyn.identity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants", indexes = {
    @Index(name = "idx_tenants_status", columnList = "status"),
    @Index(name = "idx_tenants_created_at", columnList = "created_at")
})
public class Tenant {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 50)
    private String status = "DRAFT";

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "admin_bootstrapped_at")
    private Instant adminBootstrappedAt;

    @Column(name = "admin_bootstrap_user_id")
    private UUID adminBootstrapUserId;

    @Column(name = "compliance_hold", nullable = false)
    private Boolean complianceHold = false;

    @Column(name = "compliance_hold_reason")
    private String complianceHoldReason;

    @Column(name = "delete_requested_at")
    private Instant deleteRequestedAt;

    @Column(name = "read_only", nullable = false)
    private Boolean readOnly = false;

    @Column(name = "read_only_reason")
    private String readOnlyReason;

    @Column(name = "read_only_since")
    private Instant readOnlySince;

    @Column(name = "plan_code")
    private String planCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PrePersist
    protected void onCreate() {
        if (tenantId == null) {
            tenantId = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
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

    public Boolean getComplianceHold() {
        return complianceHold;
    }

    public void setComplianceHold(Boolean complianceHold) {
        this.complianceHold = complianceHold;
    }

    public String getComplianceHoldReason() {
        return complianceHoldReason;
    }

    public void setComplianceHoldReason(String complianceHoldReason) {
        this.complianceHoldReason = complianceHoldReason;
    }

    public Instant getDeleteRequestedAt() {
        return deleteRequestedAt;
    }

    public void setDeleteRequestedAt(Instant deleteRequestedAt) {
        this.deleteRequestedAt = deleteRequestedAt;
    }

    public Boolean getReadOnly() {
        return readOnly;
    }

    public void setReadOnly(Boolean readOnly) {
        this.readOnly = readOnly;
    }

    public String getReadOnlyReason() {
        return readOnlyReason;
    }

    public void setReadOnlyReason(String readOnlyReason) {
        this.readOnlyReason = readOnlyReason;
    }

    public Instant getReadOnlySince() {
        return readOnlySince;
    }

    public void setReadOnlySince(Instant readOnlySince) {
        this.readOnlySince = readOnlySince;
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
