package io.regulyn.connector.service;

import com.regulyn.auth.apikey.ApiKeyValidator;
import com.regulyn.auth.client.IdentityAuthClient;
import com.regulyn.auth.client.ValidationResult;
import org.springframework.stereotype.Service;

/**
 * Production API key validator that calls identity-tenant-service.
 */
@Service
public class RestApiKeyValidator implements ApiKeyValidator {

    private final IdentityAuthClient identityAuthClient;

    public RestApiKeyValidator(IdentityAuthClient identityAuthClient) {
        this.identityAuthClient = identityAuthClient;
    }

    @Override
    public ApiKeyValidationResult validate(String apiKeyHash) {
        // Note: The filter passes the raw API key, not the hash
        // The identity service will hash it for lookup
        ValidationResult result = identityAuthClient.validateApiKey(apiKeyHash);

        if (result.isValid()) {
            return ApiKeyValidationResult.valid(result.getTenantId(), "api-key");
        }

        return ApiKeyValidationResult.invalid();
    }
}
