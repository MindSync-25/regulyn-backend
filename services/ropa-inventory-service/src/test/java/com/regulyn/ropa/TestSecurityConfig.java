package com.regulyn.ropa;

import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.outbox.OutboxWriter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public AuditWriter auditWriter() {
        return mock(AuditWriter.class);
    }

    @Bean
    @Primary
    public OutboxWriter outboxWriter() {
        return mock(OutboxWriter.class);
    }
}
