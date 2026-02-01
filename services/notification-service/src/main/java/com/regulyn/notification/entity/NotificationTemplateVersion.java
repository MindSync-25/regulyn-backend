package com.regulyn.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "notification_template_versions",
    schema = "notification",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_notification_template_version", columnNames = {"tenant_id", "template_id", "version_number"})
    }
)
public class NotificationTemplateVersion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "version_id")
    private UUID versionId;
    
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;
    
    @Column(name = "template_id", nullable = false)
    private UUID templateId;
    
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;
    
    @Column(name = "status", nullable = false, length = 50)
    private String status = "DRAFT";
    
    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;
    
    @Column(name = "published_at")
    private Instant publishedAt;
    
    @Column(name = "retired_at")
    private Instant retiredAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
    
    // Getters and Setters
    public UUID getVersionId() {
        return versionId;
    }
    
    public void setVersionId(UUID versionId) {
        this.versionId = versionId;
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    
    public UUID getTemplateId() {
        return templateId;
    }
    
    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }
    
    public Integer getVersionNumber() {
        return versionNumber;
    }
    
    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getChangeSummary() {
        return changeSummary;
    }
    
    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }
    
    public Instant getPublishedAt() {
        return publishedAt;
    }
    
    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
    
    public Instant getRetiredAt() {
        return retiredAt;
    }
    
    public void setRetiredAt(Instant retiredAt) {
        this.retiredAt = retiredAt;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
