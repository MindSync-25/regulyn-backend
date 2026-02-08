package com.regulyn.consent.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purpose_version_history", schema = "consent")
public class PurposeVersionHistory {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "notice_id", nullable = false)
    private UUID noticeId;

    @Column(name = "purpose_key", nullable = false, length = 200)
    private String purposeKey;

    @Column(name = "from_purpose_version_id")
    private UUID fromPurposeVersionId;

    @Column(name = "to_purpose_version_id", nullable = false)
    private UUID toPurposeVersionId;

    @Column(name = "change_summary", length = 500)
    private String changeSummary;

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

    public UUID getNoticeId() {
        return noticeId;
    }

    public void setNoticeId(UUID noticeId) {
        this.noticeId = noticeId;
    }

    public String getPurposeKey() {
        return purposeKey;
    }

    public void setPurposeKey(String purposeKey) {
        this.purposeKey = purposeKey;
    }

    public UUID getFromPurposeVersionId() {
        return fromPurposeVersionId;
    }

    public void setFromPurposeVersionId(UUID fromPurposeVersionId) {
        this.fromPurposeVersionId = fromPurposeVersionId;
    }

    public UUID getToPurposeVersionId() {
        return toPurposeVersionId;
    }

    public void setToPurposeVersionId(UUID toPurposeVersionId) {
        this.toPurposeVersionId = toPurposeVersionId;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
