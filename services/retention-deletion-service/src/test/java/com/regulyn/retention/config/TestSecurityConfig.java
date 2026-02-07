package com.regulyn.retention.config;

import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.outbox.OutboxWriter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import static org.mockito.Mockito.mock;

@TestConfiguration
@Profile("test-security-mock")
@EnableWebSecurity
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
    
    /**
     * Mock AuditWriter to avoid audit_events table dependency in tests.
     * The audit_events table is from lib-common and not part of this service's schema.
     */
    @Bean
    @Primary
    public AuditWriter auditWriter() {
        return mock(AuditWriter.class);
    }
    
    /**
     * Mock OutboxWriter to avoid outbox_events tenant context issues in tests.
     * Outbox events require TenantContext which isn't available in test environment.
     */
    @Bean
    @Primary
    public OutboxWriter outboxWriter() {
        return mock(OutboxWriter.class);
    }
}