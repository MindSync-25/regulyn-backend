package io.regulyn.identity.dto;

import java.time.Instant;
import java.util.UUID;

public class ApiKeyCreateResponse {
    private UUID apiKeyId;
    private UUID tenantId;
    private String keyName;
    private String prefix;
    private Instant expiresAt;
    private String apiKey;

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

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
