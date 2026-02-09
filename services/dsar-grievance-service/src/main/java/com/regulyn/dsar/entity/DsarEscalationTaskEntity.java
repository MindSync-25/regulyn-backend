package com.regulyn.dsar.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dsar_escalation_tasks", schema = "dsar", indexes = {
        @Index(name = "ix_dsar_escalation_tasks_tenant_status_due", columnList = "tenant_id, status, due_at"),
        @Index(name = "ix_dsar_escalation_tasks_tenant_dsar", columnList = "tenant_id, dsar_id")
})
public class DsarEscalationTaskEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dsar_id", nullable = false)
    private UUID dsarId;

    @Enumerated(EnumType.STRING)
    @Column(name = "threshold", nullable = false, length = 16)
    private EscalationThreshold threshold;

    @Column(name = "threshold_days", nullable = false)
    private Integer thresholdDays;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "reached_at", nullable = false)
    private Instant reachedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private EscalationStatus status;

    @Column(name = "notification_request_id")
    private String notificationRequestId;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (reachedAt == null) {
            reachedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getDsarId() {
        return dsarId;
    }

    public void setDsarId(UUID dsarId) {
        this.dsarId = dsarId;
    }

    public EscalationThreshold getThreshold() {
        return threshold;
    }

    public void setThreshold(EscalationThreshold threshold) {
        this.threshold = threshold;
    }

    public Integer getThresholdDays() {
        return thresholdDays;
    }

    public void setThresholdDays(Integer thresholdDays) {
        this.thresholdDays = thresholdDays;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public Instant getReachedAt() {
        return reachedAt;
    }

    public void setReachedAt(Instant reachedAt) {
        this.reachedAt = reachedAt;
    }

    public EscalationStatus getStatus() {
        return status;
    }

    public void setStatus(EscalationStatus status) {
        this.status = status;
    }

    public String getNotificationRequestId() {
        return notificationRequestId;
    }

    public void setNotificationRequestId(String notificationRequestId) {
        this.notificationRequestId = notificationRequestId;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
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