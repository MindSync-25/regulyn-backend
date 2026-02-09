package io.regulyn.identity.service;

import io.regulyn.identity.dto.ValidateApiKeyResponse;
import io.regulyn.identity.entity.ApiKey;
import io.regulyn.identity.repository.ApiKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Internal service for API key validation via HTTP endpoints.
 * Used by other services to validate API keys without direct DB access.
 */
@Service
public class InternalApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyTokenService apiKeyTokenService;

    public InternalApiKeyService(ApiKeyRepository apiKeyRepository, ApiKeyTokenService apiKeyTokenService) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyTokenService = apiKeyTokenService;
    }

    @Transactional
    public ValidateApiKeyResponse validateApiKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isEmpty()) {
            return ValidateApiKeyResponse.invalid();
        }

        String apiKeyHash = apiKeyTokenService.hashHmac(rawApiKey);

        ApiKey apiKey = apiKeyRepository.findByApiKeyHash(apiKeyHash)
            .filter(candidate -> isExpectedHashAlg(candidate, "HMAC_SHA256"))
            .orElseGet(() -> {
                String legacyHash = apiKeyTokenService.hashSha256(rawApiKey);
                return apiKeyRepository.findByApiKeyHash(legacyHash)
                    .filter(candidate -> isExpectedHashAlg(candidate, "SHA256"))
                    .orElse(null);
            });

        if (apiKey == null) {
            return ValidateApiKeyResponse.invalid();
        }

        if (!Boolean.TRUE.equals(apiKey.getEnabled())) {
            return ValidateApiKeyResponse.invalid();
        }

        if (apiKey.getRevokedAt() != null) {
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

    private boolean isExpectedHashAlg(ApiKey apiKey, String expected) {
        String alg = apiKey.getHashAlg();
        if (alg == null || alg.isBlank()) {
            return "SHA256".equals(expected);
        }
        return alg.equalsIgnoreCase(expected);
    }

}
