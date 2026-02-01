package com.regulyn.employee.config;

import com.regulyn.auth.apikey.ApiKeyValidator;
import com.regulyn.events.outbox.OutboxWriter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    public OutboxWriter outboxWriter() {
        return mock(OutboxWriter.class);
    }

    @Bean
    public ApiKeyValidator apiKeyValidator() {
        return new ApiKeyValidator() {
            @Override
            public ApiKeyValidationResult validate(String apiKeyHash) {
                return new ApiKeyValidationResult(true, UUID.randomUUID(), "test-tenant");
            }
        };
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
