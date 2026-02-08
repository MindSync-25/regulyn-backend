package com.regulyn.consent.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notice_language_text", schema = "consent")
public class NoticeLanguageText {
    
    @Id
    @Column(name = "language_id")
    private UUID languageId;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "version_id", nullable = false)
    private UUID versionId;
    
    @Column(name = "language", nullable = false)
    private String language;
    
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "translation_source", length = 20)
    private String translationSource;

    @Column(name = "translation_engine", length = 80)
    private String translationEngine;

    @Column(name = "translation_engine_version", length = 80)
    private String translationEngineVersion;

    @Column(name = "translated_from_language", length = 12)
    private String translatedFromLanguage;

    @Column(name = "translated_at")
    private Instant translatedAt;
    
    @Column(name = "created_at")
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (languageId == null) {
            languageId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    // Getters and setters
    public UUID getLanguageId() {
        return languageId;
    }

    public void setLanguageId(UUID languageId) {
        this.languageId = languageId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getTranslationSource() {
        return translationSource;
    }

    public void setTranslationSource(String translationSource) {
        this.translationSource = translationSource;
    }

    public String getTranslationEngine() {
        return translationEngine;
    }

    public void setTranslationEngine(String translationEngine) {
        this.translationEngine = translationEngine;
    }

    public String getTranslationEngineVersion() {
        return translationEngineVersion;
    }

    public void setTranslationEngineVersion(String translationEngineVersion) {
        this.translationEngineVersion = translationEngineVersion;
    }

    public String getTranslatedFromLanguage() {
        return translatedFromLanguage;
    }

    public void setTranslatedFromLanguage(String translatedFromLanguage) {
        this.translatedFromLanguage = translatedFromLanguage;
    }

    public Instant getTranslatedAt() {
        return translatedAt;
    }

    public void setTranslatedAt(Instant translatedAt) {
        this.translatedAt = translatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
