package io.regulyn.connector.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ConnectorCursorState entity for persistent cursor state during incremental sync.
 */
@Entity
@Table(
    name = "connector_cursor_state",
    schema = "connector",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_connector_cursor_state_composite",
            columnNames = {"tenant_id", "connector_id", "target_id", "job_type"}
        )
    }
)
public class ConnectorCursorState {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "connector_id", nullable = false)
    private UUID connectorId;

    @Column(name = "target_id")
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 32)
    private JobType jobType;

    @Type(JsonBinaryType.class)
    @Column(name = "cursor_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> cursorJson = new HashMap<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum JobType {
        AUDIT_PULL,
        EXPORT
    }

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters

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

    public JobType getJobType() {
        return jobType;
    }

    public void setJobType(JobType jobType) {
        this.jobType = jobType;
    }

    public Map<String, Object> getCursorJson() {
        return cursorJson;
    }

    public void setCursorJson(Map<String, Object> cursorJson) {
        this.cursorJson = cursorJson;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
