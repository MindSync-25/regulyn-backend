package io.regulyn.identity.service;

import io.regulyn.identity.dto.ValidateApiKeyResponse;
import io.regulyn.identity.entity.ApiKey;
import io.regulyn.identity.repository.ApiKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;

/**
 * Internal service for API key validation via HTTP endpoints.
 * Used by other services to validate API keys without direct DB access.
 */
@Service
public class InternalApiKeyService {

    private final ApiKeyRepository apiKeyRepository;

    public InternalApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Transactional
    public ValidateApiKeyResponse validateApiKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isEmpty()) {
            return ValidateApiKeyResponse.invalid();
        }

        // Hash the raw API key using SHA-256
        String apiKeyHash = hashApiKey(rawApiKey);

        // Look up by hash
        ApiKey apiKey = apiKeyRepository.findByApiKeyHashAndEnabled(apiKeyHash, true)
                .orElse(null);

        if (apiKey == null) {
            return ValidateApiKeyResponse.invalid();
        }

        // Check expiration
        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(Instant.now())) {
            return ValidateApiKeyResponse.invalid();
        }

        // Update last_used_at asynchronously (best effort)
        apiKey.setLastUsedAt(Instant.now());
        apiKeyRepository.save(apiKey);

        // Return validation result with CONNECTOR_AGENT role
        return ValidateApiKeyResponse.valid(
                apiKey.getTenantId(),
                null, // API keys don't have a userId
                List.of("CONNECTOR_AGENT")
        );
    }

    private String hashApiKey(String rawApiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawApiKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
