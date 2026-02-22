package com.regulyn.ropa.config;

import com.regulyn.auth.apikey.ApiKeyValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * No-op ApiKeyValidator — ROPA service does not support API key auth.
     * The bean is required because ApiKeyAuthFilter (from lib-auth) is a @Component
     * and Spring picks it up via component scan.
     * @ConditionalOnMissingBean ensures a real implementation takes precedence in production.
     */
    @Bean
    @ConditionalOnMissingBean
    public ApiKeyValidator apiKeyValidator() {
        return apiKeyHash -> ApiKeyValidator.ApiKeyValidationResult.invalid();
    }
}
