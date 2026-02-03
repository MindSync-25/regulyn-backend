package com.regulyn.retention.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Tombstone records to prevent re-creation of deleted subjects.
 * Created on final deletion completion.
 */
@Entity
@Table(name = "deletion_tombstone", schema = "deletion",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "subject_ref"}))
public class DeletionTombstone {

    @Id
    @Column(name = "tombstone_id")
    private UUID tombstoneId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_ref", nullable = false, length = 200)
    private String subjectRef;

    @Column(name = "deletion_request_id", nullable = false)
    private UUID deletionRequestId;

    @Column(name = "tombstoned_at", nullable = false)
    private Instant tombstonedAt;

    @PrePersist
    protected void onCreate() {
        if (tombstoneId == null) {
            tombstoneId = UUID.randomUUID();
        }
        if (tombstonedAt == null) {
            tombstonedAt = Instant.now();
        }
    }

    // Constructors
    public DeletionTombstone() {}

    public DeletionTombstone(UUID tenantId, String subjectRef, UUID deletionRequestId) {
        this.tenantId = tenantId;
        this.subjectRef = subjectRef;
        this.deletionRequestId = deletionRequestId;
    }

    // Getters and Setters
    public UUID getTombstoneId() {
        return tombstoneId;
    }

    public void setTombstoneId(UUID tombstoneId) {
        this.tombstoneId = tombstoneId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getSubjectRef() {
        return subjectRef;
    }

    public void setSubjectRef(String subjectRef) {
        this.subjectRef = subjectRef;
    }

    public UUID getDeletionRequestId() {
        return deletionRequestId;
    }

    public void setDeletionRequestId(UUID deletionRequestId) {
        this.deletionRequestId = deletionRequestId;
    }

    public Instant getTombstonedAt() {
        return tombstonedAt;
    }

    public void setTombstonedAt(Instant tombstonedAt) {
        this.tombstonedAt = tombstonedAt;
    }
}
