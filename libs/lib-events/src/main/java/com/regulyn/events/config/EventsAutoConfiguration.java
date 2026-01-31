package com.regulyn.events.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.events.outbox.OutboxEventRepository;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.events.publisher.OutboxPublisher;
import com.regulyn.events.publisher.DefaultNoopOutboxPublisher;
import com.regulyn.events.publisher.OutboxRelayService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Manual configuration for Outbox pattern beans.
 * Import this with @Import(EventsConfiguration.class) in your service's @SpringBootApplication class.
 */
@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
public class EventsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxWriter outboxWriter(OutboxEventRepository repository, ObjectMapper objectMapper) {
        return new OutboxWriter(repository, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxPublisher outboxPublisher() {
        return new DefaultNoopOutboxPublisher();
    }

    @Bean
    @ConditionalOnProperty(value = "regulyn.outbox.relay.enabled", havingValue = "true")
    public OutboxRelayService outboxRelayService(
            OutboxPublisher publisher,
            OutboxProperties properties) {
        return new OutboxRelayService(publisher, properties);
    }
}
