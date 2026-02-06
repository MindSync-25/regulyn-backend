package io.regulyn.connector.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * ConnectorCredential entity for storing credentials with multiple provider support.
 * Supports ENV, LOCAL_DB_ENCRYPTED, and AWS_SECRETS_MANAGER providers.
 */
@Entity
@Table(
    name = "connector_credentials",
    schema = "connector",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_connector_credentials_tenant_connector",
            columnNames = {"tenant_id", "connector_id"}
        )
    }
)
public class ConnectorCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "connector_id", nullable = false)
    private UUID connectorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private CredentialProvider provider;

    @Column(name = "secret_id", length = 256)
    private String secretId;

    @Column(name = "secret_version", length = 64)
    private String secretVersion;

    @Column(name = "enc_payload")
    private byte[] encPayload;

    @Column(name = "enc_iv")
    private byte[] encIv;

    @Column(name = "enc_kid", length = 64)
    private String encKid;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum CredentialProvider {
        ENV,
        LOCAL_DB_ENCRYPTED,
        AWS_SECRETS_MANAGER
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public CredentialProvider getProvider() {
        return provider;
    }

    public void setProvider(CredentialProvider provider) {
        this.provider = provider;
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

    public byte[] getEncPayload() {
        return encPayload;
    }

    public void setEncPayload(byte[] encPayload) {
        this.encPayload = encPayload;
    }

    public byte[] getEncIv() {
        return encIv;
    }

    public void setEncIv(byte[] encIv) {
        this.encIv = encIv;
    }

    public String getEncKid() {
        return encKid;
    }

    public void setEncKid(String encKid) {
        this.encKid = encKid;
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
