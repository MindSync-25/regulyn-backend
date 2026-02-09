package com.regulyn.vendor;

import com.regulyn.events.outbox.OutboxWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "test.outbox.mock", havingValue = "true", matchIfMissing = false)
    @ConditionalOnMissingBean(OutboxWriter.class)
    public OutboxWriter outboxWriter() {
        return mock(OutboxWriter.class);
    }
}
