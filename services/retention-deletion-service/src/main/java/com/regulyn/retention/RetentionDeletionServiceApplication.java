package com.regulyn.retention;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.regulyn.retention", "com.regulyn.common", "com.regulyn.events", "com.regulyn.evidence"})
@EnableJpaRepositories(basePackages = {"com.regulyn.retention", "com.regulyn.events.outbox", "com.regulyn.common.audit"})
@EntityScan(basePackages = {"com.regulyn.retention", "com.regulyn.events.outbox", "com.regulyn.common.audit"})
public class RetentionDeletionServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(RetentionDeletionServiceApplication.class, args);
  }
}
