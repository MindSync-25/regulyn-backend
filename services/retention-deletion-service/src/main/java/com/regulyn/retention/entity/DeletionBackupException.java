package com.regulyn.retention.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Explicit exceptions for systems that cannot delete yet due to immutable backups or legal holds.
 * These are evidenced and approved exceptions, not failures.
 */
@Entity
@Table(name = "deletion_backup_exception", schema = "deletion",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "deletion_request_id", "system_name"}))
public class DeletionBackupException {

    @Id
    @Column(name = "exception_id")
    private UUID exceptionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "deletion_request_id", nullable = false)
    private UUID deletionRequestId;

    @Column(name = "system_name", nullable = false, length = 120)
    private String systemName;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(name = "retention_until")
    private Instant retentionUntil;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "exception_artifact_ref", nullable = false, length = 200)
    private String exceptionArtifactRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (exceptionId == null) {
            exceptionId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Constructors
    public DeletionBackupException() {}

    public DeletionBackupException(UUID tenantId, UUID deletionRequestId, String systemName,
                                   String reasonCode, String exceptionArtifactRef) {
        this.tenantId = tenantId;
        this.deletionRequestId = deletionRequestId;
        this.systemName = systemName;
        this.reasonCode = reasonCode;
        this.exceptionArtifactRef = exceptionArtifactRef;
    }

    // Getters and Setters
    public UUID getExceptionId() {
        return exceptionId;
    }

    public void setExceptionId(UUID exceptionId) {
        this.exceptionId = exceptionId;
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

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public Instant getRetentionUntil() {
        return retentionUntil;
    }

    public void setRetentionUntil(Instant retentionUntil) {
        this.retentionUntil = retentionUntil;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getExceptionArtifactRef() {
        return exceptionArtifactRef;
    }

    public void setExceptionArtifactRef(String exceptionArtifactRef) {
        this.exceptionArtifactRef = exceptionArtifactRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
