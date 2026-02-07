package com.regulyn.retention.entity;

import com.regulyn.retention.enums.DeletionExecutionPlanStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deletion_execution_plan", schema = "deletion")
public class DeletionExecutionPlan {

    @Id
    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "deletion_id", nullable = false)
    private UUID deletionId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "plan_version", nullable = false)
    private Integer planVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_status", nullable = false)
    private DeletionExecutionPlanStatus planStatus = DeletionExecutionPlanStatus.CREATED;

    @Column(name = "plan_hash_sha256", nullable = false, length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String planHashSha256;

    @Column(name = "plan_json", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String planJson = "{}";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PrePersist
    protected void onCreate() {
        if (planId == null) {
            planId = UUID.randomUUID();
        }
        if (planVersion == null) {
            planVersion = 1;
        }
        if (planStatus == null) {
            planStatus = DeletionExecutionPlanStatus.CREATED;
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

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getDeletionId() { return deletionId; }
    public void setDeletionId(UUID deletionId) { this.deletionId = deletionId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Integer getPlanVersion() { return planVersion; }
    public void setPlanVersion(Integer planVersion) { this.planVersion = planVersion; }

    public DeletionExecutionPlanStatus getPlanStatus() { return planStatus; }
    public void setPlanStatus(DeletionExecutionPlanStatus planStatus) { this.planStatus = planStatus; }

    public String getPlanHashSha256() { return planHashSha256; }
    public void setPlanHashSha256(String planHashSha256) { this.planHashSha256 = planHashSha256; }

    public String getPlanJson() { return planJson; }
    public void setPlanJson(String planJson) { this.planJson = planJson; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
}
