package io.regulyn.identity.service;

import com.regulyn.auth.apikey.ApiKeyValidator;
import io.regulyn.identity.entity.ApiKey;
import io.regulyn.identity.repository.ApiKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ApiKeyValidatorImpl implements ApiKeyValidator {

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyValidatorImpl(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    @Transactional
    public ApiKeyValidationResult validate(String apiKeyHash) {
        ApiKey apiKey = apiKeyRepository.findByApiKeyHashAndEnabled(apiKeyHash, true)
            .orElse(null);

        if (apiKey == null) {
            return ApiKeyValidationResult.invalid();
        }

        String hashAlg = apiKey.getHashAlg();
        if (hashAlg != null && !hashAlg.isBlank() && !"SHA256".equalsIgnoreCase(hashAlg)) {
            return ApiKeyValidationResult.invalid();
        }

        if (apiKey.getRevokedAt() != null) {
            return ApiKeyValidationResult.invalid();
        }

        // Check expiration
        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(Instant.now())) {
            return ApiKeyValidationResult.invalid();
        }

        // Update last_used_at
        apiKey.setLastUsedAt(Instant.now());
        apiKeyRepository.save(apiKey);

        return ApiKeyValidationResult.valid(apiKey.getTenantId(), apiKey.getKeyName());
    }
}
