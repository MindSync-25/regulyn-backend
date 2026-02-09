package com.regulyn.vendor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vendor_access_exports", schema = "vendor")
public class VendorAccessExportEntity {

    @Id
    @Column(name = "export_id")
    private UUID exportId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "range_start", nullable = false)
    private OffsetDateTime rangeStart;

    @Column(name = "range_end", nullable = false)
    private OffsetDateTime rangeEnd;

    @Column(name = "format", nullable = false)
    private String format;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "artifact_ref")
    private String artifactRef;

    @Column(name = "file_hash")
    private String fileHash;

    @Column(name = "total_events", nullable = false)
    private long totalEvents;

    @Column(name = "allowed_events", nullable = false)
    private long allowedEvents;

    @Column(name = "denied_events", nullable = false)
    private long deniedEvents;

    @Column(name = "error_events", nullable = false)
    private long errorEvents;

    @Column(name = "notes")
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (exportId == null) {
            exportId = UUID.randomUUID();
        }
        if (requestedAt == null) {
            requestedAt = OffsetDateTime.now();
        }
    }

    public UUID getExportId() {
        return exportId;
    }

    public void setExportId(UUID exportId) {
        this.exportId = exportId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getVendorId() {
        return vendorId;
    }

    public void setVendorId(UUID vendorId) {
        this.vendorId = vendorId;
    }

    public UUID getRequestedByUserId() {
        return requestedByUserId;
    }

    public void setRequestedByUserId(UUID requestedByUserId) {
        this.requestedByUserId = requestedByUserId;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(OffsetDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public OffsetDateTime getRangeStart() {
        return rangeStart;
    }

    public void setRangeStart(OffsetDateTime rangeStart) {
        this.rangeStart = rangeStart;
    }

    public OffsetDateTime getRangeEnd() {
        return rangeEnd;
    }

    public void setRangeEnd(OffsetDateTime rangeEnd) {
        this.rangeEnd = rangeEnd;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getArtifactRef() {
        return artifactRef;
    }

    public void setArtifactRef(String artifactRef) {
        this.artifactRef = artifactRef;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public long getTotalEvents() {
        return totalEvents;
    }

    public void setTotalEvents(long totalEvents) {
        this.totalEvents = totalEvents;
    }

    public long getAllowedEvents() {
        return allowedEvents;
    }

    public void setAllowedEvents(long allowedEvents) {
        this.allowedEvents = allowedEvents;
    }

    public long getDeniedEvents() {
        return deniedEvents;
    }

    public void setDeniedEvents(long deniedEvents) {
        this.deniedEvents = deniedEvents;
    }

    public long getErrorEvents() {
        return errorEvents;
    }

    public void setErrorEvents(long errorEvents) {
        this.errorEvents = errorEvents;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
