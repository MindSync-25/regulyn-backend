package com.regulyn.consent.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purpose_versions", schema = "consent")
public class PurposeVersion {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "notice_id", nullable = false)
    private UUID noticeId;

    @Column(name = "notice_version_id")
    private UUID noticeVersionId;

    @Column(name = "purpose_key", nullable = false, length = 200)
    private String purposeKey;

    @Column(name = "version_num", nullable = false)
    private Integer versionNum;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope_json", nullable = false, columnDefinition = "jsonb")
    private String scopeJson;

    @Column(name = "scope_hash_sha256", nullable = false, length = 64)
    private String scopeHashSha256;

    @Column(name = "created_by_actor_id")
    private UUID createdByActorId;

    @Column(name = "created_by_actor_type", length = 40)
    private String createdByActorType;

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

    public UUID getNoticeVersionId() {
        return noticeVersionId;
    }

    public void setNoticeVersionId(UUID noticeVersionId) {
        this.noticeVersionId = noticeVersionId;
    }

    public String getPurposeKey() {
        return purposeKey;
    }

    public void setPurposeKey(String purposeKey) {
        this.purposeKey = purposeKey;
    }

    public Integer getVersionNum() {
        return versionNum;
    }

    public void setVersionNum(Integer versionNum) {
        this.versionNum = versionNum;
    }

    public String getScopeJson() {
        return scopeJson;
    }

    public void setScopeJson(String scopeJson) {
        this.scopeJson = scopeJson;
    }

    public String getScopeHashSha256() {
        return scopeHashSha256;
    }

    public void setScopeHashSha256(String scopeHashSha256) {
        this.scopeHashSha256 = scopeHashSha256;
    }

    public UUID getCreatedByActorId() {
        return createdByActorId;
    }

    public void setCreatedByActorId(UUID createdByActorId) {
        this.createdByActorId = createdByActorId;
    }

    public String getCreatedByActorType() {
        return createdByActorType;
    }

    public void setCreatedByActorType(String createdByActorType) {
        this.createdByActorType = createdByActorType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
