package com.regulyn.retention.entity;

import com.regulyn.retention.enums.DeletionTombstoneStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deletion_tombstone", schema = "deletion")
public class DeletionTombstone {

    @Id
    @Column(name = "tombstone_id")
    private UUID tombstoneId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_type", nullable = false)
    private String subjectType;

    @Column(name = "subject_ref", nullable = false)
    private String subjectRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "tombstone_status", nullable = false)
    private DeletionTombstoneStatus tombstoneStatus = DeletionTombstoneStatus.ACTIVE;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "created_from_deletion_id")
    private UUID createdFromDeletionId;

    @Column(name = "created_artifact_id", nullable = false)
    private UUID createdArtifactId;

    @Column(name = "removed_artifact_id")
    private UUID removedArtifactId;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by")
    private UUID removedBy;

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
        if (tombstoneId == null) {
            tombstoneId = UUID.randomUUID();
        }
        if (tombstoneStatus == null) {
            tombstoneStatus = DeletionTombstoneStatus.ACTIVE;
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

    public UUID getTombstoneId() { return tombstoneId; }
    public void setTombstoneId(UUID tombstoneId) { this.tombstoneId = tombstoneId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    public String getSubjectRef() { return subjectRef; }
    public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }

    public DeletionTombstoneStatus getTombstoneStatus() { return tombstoneStatus; }
    public void setTombstoneStatus(DeletionTombstoneStatus tombstoneStatus) { this.tombstoneStatus = tombstoneStatus; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public UUID getCreatedFromDeletionId() { return createdFromDeletionId; }
    public void setCreatedFromDeletionId(UUID createdFromDeletionId) { this.createdFromDeletionId = createdFromDeletionId; }

    public UUID getCreatedArtifactId() { return createdArtifactId; }
    public void setCreatedArtifactId(UUID createdArtifactId) { this.createdArtifactId = createdArtifactId; }

    public UUID getRemovedArtifactId() { return removedArtifactId; }
    public void setRemovedArtifactId(UUID removedArtifactId) { this.removedArtifactId = removedArtifactId; }

    public Instant getRemovedAt() { return removedAt; }
    public void setRemovedAt(Instant removedAt) { this.removedAt = removedAt; }

    public UUID getRemovedBy() { return removedBy; }
    public void setRemovedBy(UUID removedBy) { this.removedBy = removedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
}
