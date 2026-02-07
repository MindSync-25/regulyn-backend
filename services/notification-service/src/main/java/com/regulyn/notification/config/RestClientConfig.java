package com.regulyn.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestClientConfig {
    
    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Bean(name = "consentRestTemplate")
    public RestTemplate consentRestTemplate(
        RestTemplateBuilder builder,
        @Value("${consent.timeout:2s}") Duration timeout
    ) {
        return builder
            .setConnectTimeout(timeout)
            .setReadTimeout(timeout)
            .build();
    }
}