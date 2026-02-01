package com.regulyn.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "notification_template_language",
    schema = "notification",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_notification_template_language", columnNames = {"tenant_id", "version_id", "language"})
    }
)
public class NotificationTemplateLanguage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "language_id")
    private UUID languageId;
    
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;
    
    @Column(name = "version_id", nullable = false)
    private UUID versionId;
    
    @Column(name = "language", nullable = false, length = 10)
    private String language;
    
    @Column(name = "subject", nullable = false, length = 500)
    private String subject;
    
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;
    
    @Column(name = "format", nullable = false, length = 50)
    private String format;
    
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
    
    // Getters and Setters
    public UUID getLanguageId() {
        return languageId;
    }
    
    public void setLanguageId(UUID languageId) {
        this.languageId = languageId;
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    
    public UUID getVersionId() {
        return versionId;
    }
    
    public void setVersionId(UUID versionId) {
        this.versionId = versionId;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public String getSubject() {
        return subject;
    }
    
    public void setSubject(String subject) {
        this.subject = subject;
    }
    
    public String getBody() {
        return body;
    }
    
    public void setBody(String body) {
        this.body = body;
    }
    
    public String getFormat() {
        return format;
    }
    
    public void setFormat(String format) {
        this.format = format;
    }
    
    public String getContentHash() {
        return contentHash;
    }
    
    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
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
