package com.regulyn.guardian.config;

import com.regulyn.auth.apikey.ApiKeyValidator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.UUID;

@TestConfiguration
public class TestApiKeyConfig {

    @Bean
    @Primary
    public ApiKeyValidator mockApiKeyValidator() {
        return apiKey -> ApiKeyValidator.ApiKeyValidationResult.valid(UUID.randomUUID(), "test-api-key");
    }
}