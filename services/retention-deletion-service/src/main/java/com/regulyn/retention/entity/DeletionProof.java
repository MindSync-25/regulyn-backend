package com.regulyn.retention.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deletion_proofs", schema = "deletion")
public class DeletionProof {

    @Id
    @Column(name = "proof_id")
    private UUID proofId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "deletion_id", nullable = false)
    private UUID deletionId;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "artifact_ref", nullable = false)
    private String artifactRef;

    @Column(name = "artifact_hash", nullable = false)
    private String artifactHash;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @PrePersist
    protected void onCreate() {
        if (proofId == null) {
            proofId = UUID.randomUUID();
        }
        if (uploadedAt == null) {
            uploadedAt = Instant.now();
        }
    }

    // Getters and setters
    public UUID getProofId() { return proofId; }
    public void setProofId(UUID proofId) { this.proofId = proofId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getDeletionId() { return deletionId; }
    public void setDeletionId(UUID deletionId) { this.deletionId = deletionId; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getArtifactRef() { return artifactRef; }
    public void setArtifactRef(String artifactRef) { this.artifactRef = artifactRef; }

    public String getArtifactHash() { return artifactHash; }
    public void setArtifactHash(String artifactHash) { this.artifactHash = artifactHash; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public Instant getUploadedAt() { return uploadedAt; }
    public UUID getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }
}
