package com.regulyn.notification.config;

import com.regulyn.auth.apikey.ApiKeyValidator;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.notification.provider.EmailSendCommand;
import com.regulyn.notification.provider.NotificationProvider;
import com.regulyn.notification.provider.ProviderResult;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestSecurityConfig {

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

    @Bean
    @Primary
    public ApiKeyValidator apiKeyValidator() {
        return mock(ApiKeyValidator.class);
    }

    @Bean
    @Primary
    public NotificationProvider notificationProvider() {
        return new NotificationProvider() {
            @Override
            public ProviderResult sendEmail(EmailSendCommand command) {
                return ProviderResult.success(getProviderName(), "test-message-id");
            }

            @Override
            public String getChannel() {
                return "EMAIL";
            }

            @Override
            public String getProviderName() {
                return "TEST";
            }
        };
    }
}
