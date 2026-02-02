package io.regulyn.connector.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Connector schedule for automatic sync jobs.
 */
@Entity
@Table(name = "connector_schedules", schema = "connector")
public class ConnectorSchedule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "schedule_id")
    private UUID scheduleId;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "connector_id", nullable = false)
    private UUID connectorId;
    
    @Column(name = "target_id", nullable = false)
    private UUID targetId;
    
    @Column(name = "job_type", nullable = false)
    private String jobType; // AUDIT_PULL, EXPORT, SYNC
    
    @Column(name = "cron_expr")
    private String cronExpr;
    
    @Column(name = "interval_seconds")
    private Integer intervalSeconds;
    
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
    
    @Column(name = "last_run_at")
    private Instant lastRunAt;
    
    @Column(name = "next_run_at")
    private Instant nextRunAt;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    
    // Getters and Setters
    public UUID getScheduleId() {
        return scheduleId;
    }
    
    public void setScheduleId(UUID scheduleId) {
        this.scheduleId = scheduleId;
    }
    
    public UUID getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }
    
    public UUID getConnectorId() {
        return connectorId;
    }
    
    public void setConnectorId(UUID connectorId) {
        this.connectorId = connectorId;
    }
    
    public UUID getTargetId() {
        return targetId;
    }
    
    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
    }
    
    public String getJobType() {
        return jobType;
    }
    
    public void setJobType(String jobType) {
        this.jobType = jobType;
    }
    
    public String getCronExpr() {
        return cronExpr;
    }
    
    public void setCronExpr(String cronExpr) {
        this.cronExpr = cronExpr;
    }
    
    public Integer getIntervalSeconds() {
        return intervalSeconds;
    }
    
    public void setIntervalSeconds(Integer intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public Instant getLastRunAt() {
        return lastRunAt;
    }
    
    public void setLastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }
    
    public Instant getNextRunAt() {
        return nextRunAt;
    }
    
    public void setNextRunAt(Instant nextRunAt) {
        this.nextRunAt = nextRunAt;
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
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
