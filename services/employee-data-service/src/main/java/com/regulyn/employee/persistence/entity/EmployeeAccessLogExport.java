package com.regulyn.employee.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "employee_access_log_exports", schema = "employee")
public class EmployeeAccessLogExport {

    public enum ExportScope {
        BY_EMPLOYEE,
        BY_USER,
        BY_DATE_RANGE
    }

    public enum Status {
        CREATED,
        ARTIFACT_STORED,
        FAILED_RETRYABLE,
        FAILED_TERMINAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "access_export_id")
    private UUID accessExportId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "export_scope", nullable = false)
    private ExportScope exportScope;

    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "from_ts")
    private OffsetDateTime fromTs;

    @Column(name = "to_ts")
    private OffsetDateTime toTs;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "artifact_ref")
    private String artifactRef;

    @Column(name = "artifact_hash")
    private String artifactHash;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getAccessExportId() {
        return accessExportId;
    }

    public void setAccessExportId(UUID accessExportId) {
        this.accessExportId = accessExportId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public ExportScope getExportScope() {
        return exportScope;
    }

    public void setExportScope(ExportScope exportScope) {
        this.exportScope = exportScope;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public OffsetDateTime getFromTs() {
        return fromTs;
    }

    public void setFromTs(OffsetDateTime fromTs) {
        this.fromTs = fromTs;
    }

    public OffsetDateTime getToTs() {
        return toTs;
    }

    public void setToTs(OffsetDateTime toTs) {
        this.toTs = toTs;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getArtifactRef() {
        return artifactRef;
    }

    public void setArtifactRef(String artifactRef) {
        this.artifactRef = artifactRef;
    }

    public String getArtifactHash() {
        return artifactHash;
    }

    public void setArtifactHash(String artifactHash) {
        this.artifactHash = artifactHash;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
