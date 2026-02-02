package io.regulyn.connector.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Connector run record (for scheduled, webhook, or manual execution).
 */
@Entity
@Table(name = "connector_runs", schema = "connector")
public class ConnectorRun {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "run_id")
    private UUID runId;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "connector_id", nullable = false)
    private UUID connectorId;
    
    @Column(name = "target_id")
    private UUID targetId;
    
    @Column(name = "run_type", nullable = false)
    private String runType; // SCHEDULED, WEBHOOK, MANUAL
    
    @Column(name = "schedule_id")
    private UUID scheduleId;
    
    @Column(name = "webhook_event_id")
    private UUID webhookEventId;
    
    @Column(name = "job_type", nullable = false)
    private String jobType;
    
    @Column(name = "status", nullable = false)
    private String status; // PENDING, RUNNING, COMPLETED, FAILED
    
    @Column(name = "scheduled_fire_time")
    private Instant scheduledFireTime;
    
    @Column(name = "started_at")
    private Instant startedAt;
    
    @Column(name = "finished_at")
    private Instant finishedAt;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "receipt", columnDefinition = "jsonb")
    private Map<String, Object> receipt;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    
    // Getters and Setters
    public UUID getRunId() {
        return runId;
    }
    
    public void setRunId(UUID runId) {
        this.runId = runId;
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
    
    public String getRunType() {
        return runType;
    }
    
    public void setRunType(String runType) {
        this.runType = runType;
    }
    
    public UUID getScheduleId() {
        return scheduleId;
    }
    
    public void setScheduleId(UUID scheduleId) {
        this.scheduleId = scheduleId;
    }
    
    public UUID getWebhookEventId() {
        return webhookEventId;
    }
    
    public void setWebhookEventId(UUID webhookEventId) {
        this.webhookEventId = webhookEventId;
    }
    
    public String getJobType() {
        return jobType;
    }
    
    public void setJobType(String jobType) {
        this.jobType = jobType;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Instant getScheduledFireTime() {
        return scheduledFireTime;
    }
    
    public void setScheduledFireTime(Instant scheduledFireTime) {
        this.scheduledFireTime = scheduledFireTime;
    }
    
    public Instant getStartedAt() {
        return startedAt;
    }
    
    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }
    
    public Instant getFinishedAt() {
        return finishedAt;
    }
    
    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
    
    public Map<String, Object> getReceipt() {
        return receipt;
    }
    
    public void setReceipt(Map<String, Object> receipt) {
        this.receipt = receipt;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getIdempotencyKey() {
        return idempotencyKey;
    }
    
    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
