package com.regulyn.observability.config;

import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class ObservabilityConfig {
  
  @Bean
  public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(
      @Value("${spring.application.name:regulyn-service}") String applicationName) {
    return registry -> registry.config().commonTags("application", applicationName);
  }
}
