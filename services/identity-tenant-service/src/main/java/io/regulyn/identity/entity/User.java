package io.regulyn.identity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", 
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_tenant_email", columnNames = {"tenant_id", "email"})
    },
    indexes = {
        @Index(name = "idx_users_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_users_tenant_email", columnList = "tenant_id, email"),
        @Index(name = "idx_users_email", columnList = "email")
    })
public class User {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_reason")
    private String lockedReason;

    @Column(name = "locked_by_user_id")
    private UUID lockedByUserId;

    @PrePersist
    protected void onCreate() {
        if (userId == null) {
            userId = UUID.randomUUID();
        }
        createdAt = Instant.now();
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
    }

    public String getLockedReason() {
        return lockedReason;
    }

    public void setLockedReason(String lockedReason) {
        this.lockedReason = lockedReason;
    }

    public UUID getLockedByUserId() {
        return lockedByUserId;
    }

    public void setLockedByUserId(UUID lockedByUserId) {
        this.lockedByUserId = lockedByUserId;
    }

    public boolean isLocked() {
        return lockedAt != null;
    }
}
