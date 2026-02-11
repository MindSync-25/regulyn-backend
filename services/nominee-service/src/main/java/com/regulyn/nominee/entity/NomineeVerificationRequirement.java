package com.regulyn.nominee.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "nominee_verification_requirements", schema = "nominee")
public class NomineeVerificationRequirement {

    @EmbeddedId
    private NomineeVerificationRequirementId id;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    @Column(name = "min_docs", nullable = false)
    private int minDocs = 1;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public NomineeVerificationRequirementId getId() { return id; }
    public void setId(NomineeVerificationRequirementId id) { this.id = id; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public int getMinDocs() { return minDocs; }
    public void setMinDocs(int minDocs) { this.minDocs = minDocs; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
