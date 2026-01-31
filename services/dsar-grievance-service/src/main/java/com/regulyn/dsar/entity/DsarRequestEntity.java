package com.regulyn.dsar.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dsar_requests", schema = "dsar", indexes = {
    @Index(name = "idx_dsar_tenant_status", columnList = "tenant_id,status"),
    @Index(name = "idx_dsar_request_id", columnList = "request_id"),
    @Index(name = "idx_dsar_created", columnList = "created_at")
})
public class DsarRequestEntity {

    @Id
    @Column(name = "request_id_pk")
    private UUID requestIdPk;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    @Column(name = "request_type", nullable = false)
    private String requestType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "requester_email", nullable = false)
    private String requesterEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;
    
    @Column(name = "data_principal_id")
    private UUID dataPrincipalId;
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Column(name = "due_at", nullable = false)
    private Instant dueAt;
    
    @Column(name = "assigned_to")
    private UUID assignedTo;
    
    @Column(name = "requires_approval")
    private Boolean requiresApproval = false;
    
    @Column(name = "approved_by")
    private UUID approvedBy;
    
    @Column(name = "approved_at")
    private Instant approvedAt;
    
    @Column(name = "closed_at")
    private Instant closedAt;
    
    @Column(name = "close_evidence_bundle_id")
    private UUID closeEvidenceBundleId;
    
    @Column(name = "close_notes")
    private String closeNotes;
    
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    
    @Column(name = "details_json", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String detailsJson = "{}";
    
    @Column(name = "sla_breached", nullable = false)
    private Boolean slaBreached = false;
    
    @Column(name = "metadata")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata = "{}";

    @PrePersist
    protected void onCreate() {
        if (requestIdPk == null) {
            requestIdPk = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (dueAt == null) {
            dueAt = Instant.now().plusSeconds(90L * 24 * 60 * 60); // 90 days
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters
    public UUID getRequestIdPk() {
        return requestIdPk;
    }

    public void setRequestIdPk(UUID requestIdPk) {
        this.requestIdPk = requestIdPk;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    public void setRequesterEmail(String requesterEmail) {
        this.requesterEmail = requesterEmail;
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

    public UUID getDataPrincipalId() {
        return dataPrincipalId;
    }

    public void setDataPrincipalId(UUID dataPrincipalId) {
        this.dataPrincipalId = dataPrincipalId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public UUID getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(UUID assignedTo) {
        this.assignedTo = assignedTo;
    }

    public Boolean getRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(Boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(UUID approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public UUID getCloseEvidenceBundleId() {
        return closeEvidenceBundleId;
    }

    public void setCloseEvidenceBundleId(UUID closeEvidenceBundleId) {
        this.closeEvidenceBundleId = closeEvidenceBundleId;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getCloseNotes() {
        return closeNotes;
    }

    public void setCloseNotes(String closeNotes) {
        this.closeNotes = closeNotes;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }

    public Boolean getSlaBreached() {
        return slaBreached;
    }

    public void setSlaBreached(Boolean slaBreached) {
        this.slaBreached = slaBreached;
    }
}
