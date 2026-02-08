package com.regulyn.consent.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "communication_consent_ledger", schema = "consent")
public class CommunicationConsentLedger {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "data_principal_id", nullable = false)
    private UUID dataPrincipalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private CommunicationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private CommunicationConsentState state;

    @Column(name = "source", nullable = false, length = 80)
    private String source;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_type", length = 40)
    private String actorType;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(name = "effective_time_bucket", nullable = false)
    private Instant effectiveTimeBucket;

    @Column(name = "language_code", length = 12)
    private String languageCode;

    @Column(name = "consent_text_hash_sha256", nullable = false, length = 64)
    private String consentTextHashSha256;

    @Column(name = "notice_language_text_id")
    private UUID noticeLanguageTextId;

    @Column(name = "evidence_artifact_id")
    private UUID evidenceArtifactId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
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

    public UUID getDataPrincipalId() {
        return dataPrincipalId;
    }

    public void setDataPrincipalId(UUID dataPrincipalId) {
        this.dataPrincipalId = dataPrincipalId;
    }

    public CommunicationChannel getChannel() {
        return channel;
    }

    public void setChannel(CommunicationChannel channel) {
        this.channel = channel;
    }

    public CommunicationConsentState getState() {
        return state;
    }

    public void setState(CommunicationConsentState state) {
        this.state = state;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(Instant effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    public Instant getEffectiveTimeBucket() {
        return effectiveTimeBucket;
    }

    public void setEffectiveTimeBucket(Instant effectiveTimeBucket) {
        this.effectiveTimeBucket = effectiveTimeBucket;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public String getConsentTextHashSha256() {
        return consentTextHashSha256;
    }

    public void setConsentTextHashSha256(String consentTextHashSha256) {
        this.consentTextHashSha256 = consentTextHashSha256;
    }

    public UUID getNoticeLanguageTextId() {
        return noticeLanguageTextId;
    }

    public void setNoticeLanguageTextId(UUID noticeLanguageTextId) {
        this.noticeLanguageTextId = noticeLanguageTextId;
    }

    public UUID getEvidenceArtifactId() {
        return evidenceArtifactId;
    }

    public void setEvidenceArtifactId(UUID evidenceArtifactId) {
        this.evidenceArtifactId = evidenceArtifactId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
