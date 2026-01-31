package com.regulyn.consent.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notice_versions", schema = "consent")
public class NoticeVersion {
    
    @Id
    @Column(name = "version_id")
    private UUID versionId;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "notice_id", nullable = false)
    private UUID noticeId;
    
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;
    
    @Column(name = "change_summary")
    private String changeSummary;
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Column(name = "published_at")
    private Instant publishedAt;
    
    @Column(name = "created_at")
    private Instant createdAt;
    
    @Column(name = "created_by")
    private UUID createdBy;
    
    @Column(name = "evidence_id")
    private UUID evidenceId;
    
    @PrePersist
    protected void onCreate() {
        if (versionId == null) {
            versionId = UUID.randomUUID();
        }
        if (status == null) {
            status = "DRAFT";
        }
        createdAt = Instant.now();
    }

    // Getters and setters
    public UUID getVersionId() {
        return versionId;
    }

    public void setVersionId(UUID versionId) {
        this.versionId = versionId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getNoticeId() {
        return noticeId;
    }

    public void setNoticeId(UUID noticeId) {
        this.noticeId = noticeId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(UUID evidenceId) {
        this.evidenceId = evidenceId;
    }
}
