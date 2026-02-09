package io.regulyn.identity.dto;

import java.time.Instant;
import java.util.UUID;

public class ApiKeyRevokeResponse {
    private UUID apiKeyId;
    private Instant revokedAt;

    public UUID getApiKeyId() {
        return apiKeyId;
    }

    public void setApiKeyId(UUID apiKeyId) {
        this.apiKeyId = apiKeyId;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
