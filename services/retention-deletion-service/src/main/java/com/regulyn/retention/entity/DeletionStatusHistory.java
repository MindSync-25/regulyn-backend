package com.regulyn.retention.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deletion_status_history", schema = "deletion")
public class DeletionStatusHistory {

    @Id
    @Column(name = "history_id")
    private UUID historyId;

    @Column(name = "deletion_id", nullable = false)
    private UUID deletionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "reason")
    private String reason;

    @PrePersist
    protected void onCreate() {
        if (historyId == null) {
            historyId = UUID.randomUUID();
        }
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }

    public UUID getHistoryId() { return historyId; }
    public UUID getDeletionId() { return deletionId; }
    public UUID getTenantId() { return tenantId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public Instant getChangedAt() { return changedAt; }
    public UUID getChangedBy() { return changedBy; }
    public String getReason() { return reason; }
}
