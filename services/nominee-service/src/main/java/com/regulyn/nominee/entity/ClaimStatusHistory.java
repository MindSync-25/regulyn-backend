package com.regulyn.nominee.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claim_status_history", schema = "nominee")
public class ClaimStatusHistory {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Column(name = "transitioned_at", nullable = false)
    private Instant transitionedAt;

    @Column(name = "transitioned_by")
    private UUID transitionedBy;

    @Column(name = "reason")
    private String reason;

    @Column(name = "metadata")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata = "{}";

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (transitionedAt == null) {
            transitionedAt = Instant.now();
        }
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getClaimId() { return claimId; }
    public void setClaimId(UUID claimId) { this.claimId = claimId; }

    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }

    public Instant getTransitionedAt() { return transitionedAt; }
    public void setTransitionedAt(Instant transitionedAt) { this.transitionedAt = transitionedAt; }

    public UUID getTransitionedBy() { return transitionedBy; }
    public void setTransitionedBy(UUID transitionedBy) { this.transitionedBy = transitionedBy; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
