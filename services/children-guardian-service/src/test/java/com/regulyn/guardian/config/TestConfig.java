package com.regulyn.guardian.config;

import com.regulyn.auth.apikey.ApiKeyValidator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration providing mock beans for integration tests.
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public ApiKeyValidator mockApiKeyValidator() {
        return new ApiKeyValidator() {
            @Override
            public ApiKeyValidationResult validate(String apiKey) {
                // For tests, accept any API key as valid
                return ApiKeyValidationResult.valid(java.util.UUID.randomUUID(), "test-api-key");
            }
        };
    }
}
