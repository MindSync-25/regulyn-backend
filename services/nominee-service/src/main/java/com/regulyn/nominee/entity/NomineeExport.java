package com.regulyn.nominee.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "nominee_exports", schema = "nominee")
public class NomineeExport {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "nominee_id", nullable = false)
    private UUID nomineeId;

    @Column(name = "format", nullable = false)
    private String format;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "download_url")
    private String downloadUrl;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "metadata")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata = "{}";

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getNomineeId() { return nomineeId; }
    public void setNomineeId(UUID nomineeId) { this.nomineeId = nomineeId; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
