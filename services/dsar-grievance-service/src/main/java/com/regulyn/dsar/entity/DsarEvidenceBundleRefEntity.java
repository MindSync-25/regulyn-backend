package com.regulyn.dsar.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dsar_evidence_bundle_refs", schema = "dsar", indexes = {
        @Index(name = "ix_dsar_bundle_refs_tenant_dsar_created", columnList = "tenant_id, dsar_id, created_at")
})
public class DsarEvidenceBundleRefEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dsar_id", nullable = false)
    private UUID dsarId;

    @Column(name = "close_event_id", nullable = false)
    private UUID closeEventId;

    @Column(name = "bundle_ref")
    private String bundleRef;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "bundle_sha256", columnDefinition = "char(64)")
    private String bundleSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private BundleRefStatus status;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

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

    public UUID getDsarId() {
        return dsarId;
    }

    public void setDsarId(UUID dsarId) {
        this.dsarId = dsarId;
    }

    public UUID getCloseEventId() {
        return closeEventId;
    }

    public void setCloseEventId(UUID closeEventId) {
        this.closeEventId = closeEventId;
    }

    public String getBundleRef() {
        return bundleRef;
    }

    public void setBundleRef(String bundleRef) {
        this.bundleRef = bundleRef;
    }

    public String getBundleSha256() {
        return bundleSha256;
    }

    public void setBundleSha256(String bundleSha256) {
        this.bundleSha256 = bundleSha256;
    }

    public BundleRefStatus getStatus() {
        return status;
    }

    public void setStatus(BundleRefStatus status) {
        this.status = status;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
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
}