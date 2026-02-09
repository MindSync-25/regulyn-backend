package io.regulyn.identity.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_invites", indexes = {
    @Index(name = "idx_user_invites_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_user_invites_expires_at", columnList = "expires_at"),
    @Index(name = "idx_user_invites_token_hash", columnList = "token_hash")
})
public class UserInvite {

    @Id
    @Column(name = "invite_id", nullable = false, updatable = false)
    private UUID inviteId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "roles_json", nullable = false, columnDefinition = "jsonb")
    private String rolesJson;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "token_hash_alg", nullable = false, length = 50)
    private String tokenHashAlg = "HMAC_SHA256";

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "used_by_user_id")
    private UUID usedByUserId;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getInviteId() {
        return inviteId;
    }

    public void setInviteId(UUID inviteId) {
        this.inviteId = inviteId;
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

    public String getRolesJson() {
        return rolesJson;
    }

    public void setRolesJson(String rolesJson) {
        this.rolesJson = rolesJson;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getTokenHashAlg() {
        return tokenHashAlg;
    }

    public void setTokenHashAlg(String tokenHashAlg) {
        this.tokenHashAlg = tokenHashAlg;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public UUID getUsedByUserId() {
        return usedByUserId;
    }

    public void setUsedByUserId(UUID usedByUserId) {
        this.usedByUserId = usedByUserId;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(UUID createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
