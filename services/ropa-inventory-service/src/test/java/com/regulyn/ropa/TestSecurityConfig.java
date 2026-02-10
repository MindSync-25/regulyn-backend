package com.regulyn.ropa;

import com.regulyn.auth.apikey.ApiKeyValidator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public ApiKeyValidator apiKeyValidator() {
        return mock(ApiKeyValidator.class);
    }
}
