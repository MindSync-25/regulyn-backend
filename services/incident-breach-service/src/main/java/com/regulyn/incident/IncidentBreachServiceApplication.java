package com.regulyn.incident;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {
    "com.regulyn.incident",
    "com.regulyn.common",
    "com.regulyn.events"
})
@EnableJpaRepositories(basePackages = {
    "com.regulyn.incident.repository",
    "com.regulyn.events.outbox"
})
@EntityScan(basePackages = {
    "com.regulyn.incident.entity",
    "com.regulyn.events.outbox"
})
public class IncidentBreachServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(IncidentBreachServiceApplication.class, args);
  }
}
