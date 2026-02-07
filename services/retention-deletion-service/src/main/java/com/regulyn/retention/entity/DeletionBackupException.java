package com.regulyn.retention.entity;

import com.regulyn.retention.enums.DeletionBackupExceptionStatus;
import com.regulyn.retention.enums.DeletionBackupExceptionType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deletion_backup_exception", schema = "deletion")
public class DeletionBackupException {

    @Id
    @Column(name = "exception_id")
    private UUID exceptionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "deletion_id", nullable = false)
    private UUID deletionId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "execution_id")
    private UUID executionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false)
    private DeletionBackupExceptionType exceptionType;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "not_before", nullable = false)
    private Instant notBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DeletionBackupExceptionStatus status = DeletionBackupExceptionStatus.ACTIVE;

    @Column(name = "exception_artifact_id", nullable = false)
    private UUID exceptionArtifactId;

    @Column(name = "granted_at")
    private Instant grantedAt;

    @Column(name = "granted_by")
    private UUID grantedBy;

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
        if (exceptionId == null) {
            exceptionId = UUID.randomUUID();
        }
        if (status == null) {
            status = DeletionBackupExceptionStatus.ACTIVE;
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

    public UUID getExceptionId() { return exceptionId; }
    public void setExceptionId(UUID exceptionId) { this.exceptionId = exceptionId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getDeletionId() { return deletionId; }
    public void setDeletionId(UUID deletionId) { this.deletionId = deletionId; }

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }

    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID executionId) { this.executionId = executionId; }

    public DeletionBackupExceptionType getExceptionType() { return exceptionType; }
    public void setExceptionType(DeletionBackupExceptionType exceptionType) { this.exceptionType = exceptionType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getNotBefore() { return notBefore; }
    public void setNotBefore(Instant notBefore) { this.notBefore = notBefore; }

    public DeletionBackupExceptionStatus getStatus() { return status; }
    public void setStatus(DeletionBackupExceptionStatus status) { this.status = status; }

    public UUID getExceptionArtifactId() { return exceptionArtifactId; }
    public void setExceptionArtifactId(UUID exceptionArtifactId) { this.exceptionArtifactId = exceptionArtifactId; }

    public Instant getGrantedAt() { return grantedAt; }
    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }

    public UUID getGrantedBy() { return grantedBy; }
    public void setGrantedBy(UUID grantedBy) { this.grantedBy = grantedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
}
