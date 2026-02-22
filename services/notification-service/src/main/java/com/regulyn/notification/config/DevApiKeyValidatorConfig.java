package com.regulyn.notification.config;

import com.regulyn.auth.apikey.ApiKeyValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Dev enablement: notification-service pulls in lib-auth's ApiKeyAuthFilter (@Component).
 * That filter requires an ApiKeyValidator bean.
 *
 * In production, this should be backed by a real validator (typically from identity-tenant-service).
 * For local dev, we provide a safe default that marks all API keys as invalid.
 *
 * This still allows normal auth flows to work; the filter only *acts* when X-API-Key is present.
 */
@Configuration
@Profile("dev")
public class DevApiKeyValidatorConfig {

  @Bean
  public ApiKeyValidator apiKeyValidator() {
    return apiKeyHash -> ApiKeyValidator.ApiKeyValidationResult.invalid();
  }
}
