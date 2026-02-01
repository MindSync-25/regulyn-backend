package com.regulyn.vendor;

import com.regulyn.events.outbox.OutboxWriter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    public OutboxWriter outboxWriter() {
        return mock(OutboxWriter.class);
    }
}
