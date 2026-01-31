package com.regulyn.retention.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deletion_requests", schema = "deletion")
public class DeletionRequest {

    @Id
    @Column(name = "deletion_id")
    private UUID deletionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "subject_type", nullable = false)
    private String subjectType;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "reason")
    private String reason;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "evidence_bundle_id")
    private UUID evidenceBundleId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "requires_approval")
    private Boolean requiresApproval = true;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "proof_required")
    private Boolean proofRequired = true;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "metadata")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata = "{}";

    @PrePersist
    protected void onCreate() {
        if (deletionId == null) {
            deletionId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (dueAt == null) {
            dueAt = Instant.now().plusSeconds(30L * 24 * 60 * 60);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getDeletionId() { return deletionId; }
    public void setDeletionId(UUID deletionId) { this.deletionId = deletionId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getEvidenceBundleId() { return evidenceBundleId; }
    public void setEvidenceBundleId(UUID evidenceBundleId) { this.evidenceBundleId = evidenceBundleId; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }

    public UUID getAssignedTo() { return assignedTo; }
    public void setAssignedTo(UUID assignedTo) { this.assignedTo = assignedTo; }

    public Boolean getRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(Boolean requiresApproval) { this.requiresApproval = requiresApproval; }

    public Boolean getProofRequired() { return proofRequired; }
    public void setProofRequired(Boolean proofRequired) { this.proofRequired = proofRequired; }

    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }

    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
