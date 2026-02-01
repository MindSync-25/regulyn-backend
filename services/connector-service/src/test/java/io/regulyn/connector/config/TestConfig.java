package io.regulyn.connector.config;

import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.outbox.OutboxWriter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public OutboxWriter outboxWriter() {
        return mock(OutboxWriter.class);
    }

    @Bean
    @Primary
    public EventFactory eventFactory() {
        return mock(EventFactory.class);
    }
}
