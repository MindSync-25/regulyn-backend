package com.regulyn.vendor.config;

import com.regulyn.auth.apikey.ApiKeyValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile({"local", "test"})
public class VendorAuthConfig {

    @Bean
    @ConditionalOnMissingBean
    public ApiKeyValidator apiKeyValidator() {
        return apiKeyHash -> ApiKeyValidator.ApiKeyValidationResult.invalid();
    }
}
