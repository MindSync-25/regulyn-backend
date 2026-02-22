package com.regulyn.scanner.config;

import com.regulyn.auth.apikey.ApiKeyValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ScannerAuthConfig {

    /**
     * No-op ApiKeyValidator — scanner-service does not support API key auth.
     * Required because ApiKeyAuthFilter (lib-auth @Component) is picked up by
     * the broad component scan and needs this bean wired.
     * @ConditionalOnMissingBean ensures a real implementation takes precedence in production.
     */
    @Bean
    @ConditionalOnMissingBean
    public ApiKeyValidator apiKeyValidator() {
        return apiKeyHash -> ApiKeyValidator.ApiKeyValidationResult.invalid();
    }
}
