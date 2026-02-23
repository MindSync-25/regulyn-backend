package com.regulyn.guardian.config;

import com.regulyn.auth.apikey.ApiKeyValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * No-op ApiKeyValidator — children-guardian-service does not support API key auth.
     * Required because ApiKeyAuthFilter (from lib-auth) is picked up via component scan.
     */
    @Bean
    @ConditionalOnMissingBean
    public ApiKeyValidator apiKeyValidator() {
        return apiKeyHash -> ApiKeyValidator.ApiKeyValidationResult.invalid();
    }
}
