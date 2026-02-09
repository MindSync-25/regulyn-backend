package com.regulyn.dsar.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dsar_attachments", schema = "dsar", indexes = {
        @Index(name = "ix_dsar_attachments_tenant_dsar_created", columnList = "tenant_id, dsar_id, created_at")
})
public class DsarAttachmentEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dsar_id", nullable = false)
    private UUID dsarId;

    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type", nullable = false, length = 16)
    private AttachmentType attachmentType;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "filename")
    private String filename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256", columnDefinition = "char(64)")
    private String sha256;

    @Column(name = "artifact_ref")
    private String artifactRef;

    @Column(name = "reference_value")
    private String referenceValue;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "reference_hash", columnDefinition = "char(64)")
    private String referenceHash;

    @Column(name = "recorded_evidence_artifact_ref")
    private String recordedEvidenceArtifactRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    private String metadata = "{}";

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

    public UUID getDsarId() {
        return dsarId;
    }

    public void setDsarId(UUID dsarId) {
        this.dsarId = dsarId;
    }

    public AttachmentType getAttachmentType() {
        return attachmentType;
    }

    public void setAttachmentType(AttachmentType attachmentType) {
        this.attachmentType = attachmentType;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getArtifactRef() {
        return artifactRef;
    }

    public void setArtifactRef(String artifactRef) {
        this.artifactRef = artifactRef;
    }

    public String getReferenceValue() {
        return referenceValue;
    }

    public void setReferenceValue(String referenceValue) {
        this.referenceValue = referenceValue;
    }

    public String getReferenceHash() {
        return referenceHash;
    }

    public void setReferenceHash(String referenceHash) {
        this.referenceHash = referenceHash;
    }

    public String getRecordedEvidenceArtifactRef() {
        return recordedEvidenceArtifactRef;
    }

    public void setRecordedEvidenceArtifactRef(String recordedEvidenceArtifactRef) {
        this.recordedEvidenceArtifactRef = recordedEvidenceArtifactRef;
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

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}