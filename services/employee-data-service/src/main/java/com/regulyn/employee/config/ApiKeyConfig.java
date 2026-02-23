package com.regulyn.employee.config;

import com.regulyn.auth.apikey.ApiKeyValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ApiKeyConfig {

    /**
     * No-op ApiKeyValidator — employee-data-service does not support API key auth.
     * The bean is required because ApiKeyAuthFilter (from lib-auth) is a @Component
     * and Spring picks it up via component scan in this service.
     * ConditionalOnMissingBean ensures a real implementation takes precedence if added later.
     */
    @Bean
    @ConditionalOnMissingBean
    public ApiKeyValidator apiKeyValidator() {
        return apiKeyHash -> ApiKeyValidator.ApiKeyValidationResult.invalid();
    }

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
