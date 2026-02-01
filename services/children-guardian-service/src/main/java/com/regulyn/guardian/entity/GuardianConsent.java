package com.regulyn.guardian.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "guardian_consents", schema = "children")
public class GuardianConsent {
    
    @Id
    @Column(name = "consent_id")
    private UUID consentId;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "child_id", nullable = false)
    private UUID childId;
    
    @Column(name = "guardian_id", nullable = false)
    private UUID guardianId;
    
    @Column(name = "purpose_key", nullable = false)
    private String purposeKey;
    
    @Column(name = "consent_scope", nullable = false)
    private String consentScope;
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval = true;
    
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    
    @Column(name = "valid_from")
    private LocalDate validFrom;
    
    @Column(name = "valid_to")
    private LocalDate validTo;
    
    @Column(name = "approved_by")
    private UUID approvedBy;
    
    @Column(name = "approved_at")
    private Instant approvedAt;
    
    @Column(name = "revoked_at")
    private Instant revokedAt;
    
    @Column(name = "revoked_by")
    private UUID revokedBy;
    
    @Column(name = "revoke_reason")
    private String revokeReason;
    
    @Column(name = "closed_at")
    private Instant closedAt;
    
    @Column(name = "closure_notes")
    private String closureNotes;
    
    @Column(name = "evidence_bundle_id")
    private UUID evidenceBundleId;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    // Constructors
    public GuardianConsent() {
        this.consentId = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
    
    // Getters and Setters
    public UUID getConsentId() {
        return consentId;
    }
    
    public void setConsentId(UUID consentId) {
        this.consentId = consentId;
    }
    
    public UUID getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }
    
    public UUID getChildId() {
        return childId;
    }
    
    public void setChildId(UUID childId) {
        this.childId = childId;
    }
    
    public UUID getGuardianId() {
        return guardianId;
    }
    
    public void setGuardianId(UUID guardianId) {
        this.guardianId = guardianId;
    }
    
    public String getPurposeKey() {
        return purposeKey;
    }
    
    public void setPurposeKey(String purposeKey) {
        this.purposeKey = purposeKey;
    }
    
    public String getConsentScope() {
        return consentScope;
    }
    
    public void setConsentScope(String consentScope) {
        this.consentScope = consentScope;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public boolean isRequiresApproval() {
        return requiresApproval;
    }
    
    public void setRequiresApproval(boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }
    
    public String getIdempotencyKey() {
        return idempotencyKey;
    }
    
    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
    
    public LocalDate getValidFrom() {
        return validFrom;
    }
    
    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }
    
    public LocalDate getValidTo() {
        return validTo;
    }
    
    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
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
    
    public Instant getRevokedAt() {
        return revokedAt;
    }
    
    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
    
    public UUID getRevokedBy() {
        return revokedBy;
    }
    
    public void setRevokedBy(UUID revokedBy) {
        this.revokedBy = revokedBy;
    }
    
    public String getRevokeReason() {
        return revokeReason;
    }
    
    public void setRevokeReason(String revokeReason) {
        this.revokeReason = revokeReason;
    }
    
    public Instant getClosedAt() {
        return closedAt;
    }
    
    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }
    
    public String getClosureNotes() {
        return closureNotes;
    }
    
    public void setClosureNotes(String closureNotes) {
        this.closureNotes = closureNotes;
    }
    
    public UUID getEvidenceBundleId() {
        return evidenceBundleId;
    }
    
    public void setEvidenceBundleId(UUID evidenceBundleId) {
        this.evidenceBundleId = evidenceBundleId;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
