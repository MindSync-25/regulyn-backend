package io.regulyn.identity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_api_keys_hash", columnNames = {"api_key_hash"})
    },
    indexes = {
        @Index(name = "idx_api_keys_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_api_keys_hash", columnList = "api_key_hash"),
        @Index(name = "idx_api_keys_enabled", columnList = "enabled")
    })
public class ApiKey {

    @Id
    @Column(name = "api_key_id", nullable = false, updatable = false)
    private UUID apiKeyId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "key_name", nullable = false, length = 255)
    private String keyName;

    @Column(name = "api_key_hash", nullable = false, length = 64)
    private String apiKeyHash;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "key_version", nullable = false)
    private Integer keyVersion = 1;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "rotated_from_api_key_id")
    private UUID rotatedFromApiKeyId;

    @Column(name = "prefix", length = 16)
    private String prefix;

    @Column(name = "hash_alg", nullable = false, length = 50)
    private String hashAlg = "SHA256";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @PrePersist
    protected void onCreate() {
        if (apiKeyId == null) {
            apiKeyId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    // Getters and Setters
    public UUID getApiKeyId() {
        return apiKeyId;
    }

    public void setApiKeyId(UUID apiKeyId) {
        this.apiKeyId = apiKeyId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public void setApiKeyHash(String apiKeyHash) {
        this.apiKeyHash = apiKeyHash;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Integer getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(Integer keyVersion) {
        this.keyVersion = keyVersion;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public UUID getRotatedFromApiKeyId() {
        return rotatedFromApiKeyId;
    }

    public void setRotatedFromApiKeyId(UUID rotatedFromApiKeyId) {
        this.rotatedFromApiKeyId = rotatedFromApiKeyId;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getHashAlg() {
        return hashAlg;
    }

    public void setHashAlg(String hashAlg) {
        this.hashAlg = hashAlg;
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
}
