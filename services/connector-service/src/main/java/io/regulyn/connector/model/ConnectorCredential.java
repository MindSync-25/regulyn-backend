package io.regulyn.connector.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Connector credential stored in database with encryption at rest.
 * Supports multiple secret providers: LOCAL_DB_ENCRYPTED, AWS_SECRETS_MANAGER, ENV
 */
@Entity
@Table(name = "connector_credentials", schema = "connector")
public class ConnectorCredential {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "credential_id")
    private UUID credentialId;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "connector_id", nullable = false)
    private UUID connectorId;
    
    @Column(name = "credential_type", nullable = false)
    private String credentialType;
    
    @Column(name = "encrypted_value")
    private byte[] encryptedValue;
    
    @Column(name = "encryption_key_ref")
    private String encryptionKeyRef;
    
    @Column(name = "aws_secret_arn")
    private String awsSecretArn;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "secret_provider", nullable = false, length = 50)
    private SecretProviderType secretProvider = SecretProviderType.LOCAL_DB_ENCRYPTED;
    
    @Column(name = "secret_id", length = 500)
    private String secretId;
    
    @Column(name = "secret_version", length = 100)
    private String secretVersion;
    
    @Column(name = "last_resolved_at")
    private Instant lastResolvedAt;
    
    @Column(name = "resolve_fail_count", nullable = false)
    private int resolveFailCount = 0;
    
    @Column(name = "expires_at")
    private Instant expiresAt;
    
    @Column(name = "rotation_required", nullable = false)
    private boolean rotationRequired = false;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    
    public enum SecretProviderType {
        LOCAL_DB_ENCRYPTED,
        AWS_SECRETS_MANAGER,
        ENV
    }
    
    // Getters and Setters
    public UUID getCredentialId() {
        return credentialId;
    }
    
    public void setCredentialId(UUID credentialId) {
        this.credentialId = credentialId;
    }
    
    public UUID getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }
    
    public UUID getConnectorId() {
        return connectorId;
    }
    
    public void setConnectorId(UUID connectorId) {
        this.connectorId = connectorId;
    }
    
    public String getCredentialType() {
        return credentialType;
    }
    
    public void setCredentialType(String credentialType) {
        this.credentialType = credentialType;
    }
    
    public byte[] getEncryptedValue() {
        return encryptedValue;
    }
    
    public void setEncryptedValue(byte[] encryptedValue) {
        this.encryptedValue = encryptedValue;
    }
    
    public String getEncryptionKeyRef() {
        return encryptionKeyRef;
    }
    
    public void setEncryptionKeyRef(String encryptionKeyRef) {
        this.encryptionKeyRef = encryptionKeyRef;
    }
    
    public String getAwsSecretArn() {
        return awsSecretArn;
    }
    
    public void setAwsSecretArn(String awsSecretArn) {
        this.awsSecretArn = awsSecretArn;
    }
    
    public SecretProviderType getSecretProvider() {
        return secretProvider;
    }
    
    public void setSecretProvider(SecretProviderType secretProvider) {
        this.secretProvider = secretProvider;
    }
    
    public String getSecretId() {
        return secretId;
    }
    
    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }
    
    public String getSecretVersion() {
        return secretVersion;
    }
    
    public void setSecretVersion(String secretVersion) {
        this.secretVersion = secretVersion;
    }
    
    public Instant getLastResolvedAt() {
        return lastResolvedAt;
    }
    
    public void setLastResolvedAt(Instant lastResolvedAt) {
        this.lastResolvedAt = lastResolvedAt;
    }
    
    public int getResolveFailCount() {
        return resolveFailCount;
    }
    
    public void setResolveFailCount(int resolveFailCount) {
        this.resolveFailCount = resolveFailCount;
    }
    
    public void incrementFailCount() {
        this.resolveFailCount++;
    }
    
    public void resetFailCount() {
        this.resolveFailCount = 0;
    }
    
    public Instant getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public boolean isRotationRequired() {
        return rotationRequired;
    }
    
    public void setRotationRequired(boolean rotationRequired) {
        this.rotationRequired = rotationRequired;
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
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
