package com.regulyn.guardian.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "guardians", schema = "children")
public class Guardian {
    
    @Id
    @Column(name = "guardian_id")
    private UUID guardianId;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "child_id", nullable = false)
    private UUID childId;
    
    @Column(name = "guardian_name", nullable = false)
    private String guardianName;
    
    @Column(name = "guardian_email")
    private String guardianEmail;
    
    @Column(name = "guardian_phone")
    private String guardianPhone;
    
    @Column(name = "relationship", nullable = false)
    private String relationship;
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Column(name = "verification_required", nullable = false)
    private boolean verificationRequired = true;
    
    @Column(name = "verified_at")
    private Instant verifiedAt;
    
    @Column(name = "verified_by")
    private UUID verifiedBy;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    // Constructors
    public Guardian() {
        this.guardianId = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
    
    // Getters and Setters
    public UUID getGuardianId() {
        return guardianId;
    }
    
    public void setGuardianId(UUID guardianId) {
        this.guardianId = guardianId;
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
    
    public String getGuardianName() {
        return guardianName;
    }
    
    public void setGuardianName(String guardianName) {
        this.guardianName = guardianName;
    }
    
    public String getGuardianEmail() {
        return guardianEmail;
    }
    
    public void setGuardianEmail(String guardianEmail) {
        this.guardianEmail = guardianEmail;
    }
    
    public String getGuardianPhone() {
        return guardianPhone;
    }
    
    public void setGuardianPhone(String guardianPhone) {
        this.guardianPhone = guardianPhone;
    }
    
    public String getRelationship() {
        return relationship;
    }
    
    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public boolean isVerificationRequired() {
        return verificationRequired;
    }
    
    public void setVerificationRequired(boolean verificationRequired) {
        this.verificationRequired = verificationRequired;
    }
    
    public Instant getVerifiedAt() {
        return verifiedAt;
    }
    
    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
    
    public UUID getVerifiedBy() {
        return verifiedBy;
    }
    
    public void setVerifiedBy(UUID verifiedBy) {
        this.verifiedBy = verifiedBy;
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
