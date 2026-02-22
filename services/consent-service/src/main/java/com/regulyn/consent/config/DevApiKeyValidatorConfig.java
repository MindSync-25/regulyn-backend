package com.regulyn.consent.config;

import com.regulyn.auth.apikey.ApiKeyValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Dev enablement: consent-service pulls in lib-auth components (e.g. TenantContextFilter, ApiKeyAuthFilter).
 * ApiKeyAuthFilter requires an ApiKeyValidator bean.
 *
 * In production, this should be backed by a real validator (typically from identity-tenant-service).
 * For local dev, we provide a safe default that marks all API keys as invalid.
 *
 * This still allows normal browser/JWT/header flows to work; the API-key filter only acts when X-API-Key is present.
 */
@Configuration
@Profile("dev")
public class DevApiKeyValidatorConfig {

  @Bean
  public ApiKeyValidator apiKeyValidator() {
    return apiKeyHash -> ApiKeyValidator.ApiKeyValidationResult.invalid();
  }
}
