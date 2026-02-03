package com.regulyn.retention.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Execution plan for cascading deletion across integrated systems.
 * One plan per deletion request.
 */
@Entity
@Table(name = "deletion_execution_plan", schema = "deletion")
public class DeletionExecutionPlan {

    @Id
    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "deletion_request_id", nullable = false, unique = true)
    private UUID deletionRequestId;

    @Column(name = "plan_version", nullable = false)
    private Integer planVersion = 1;

    @Column(name = "plan_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String planJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (planId == null) {
            planId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Constructors
    public DeletionExecutionPlan() {}

    public DeletionExecutionPlan(UUID tenantId, UUID deletionRequestId, String planJson) {
        this.tenantId = tenantId;
        this.deletionRequestId = deletionRequestId;
        this.planJson = planJson;
    }

    // Getters and Setters
    public UUID getPlanId() {
        return planId;
    }

    public void setPlanId(UUID planId) {
        this.planId = planId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getDeletionRequestId() {
        return deletionRequestId;
    }

    public void setDeletionRequestId(UUID deletionRequestId) {
        this.deletionRequestId = deletionRequestId;
    }

    public Integer getPlanVersion() {
        return planVersion;
    }

    public void setPlanVersion(Integer planVersion) {
        this.planVersion = planVersion;
    }

    public String getPlanJson() {
        return planJson;
    }

    public void setPlanJson(String planJson) {
        this.planJson = planJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
