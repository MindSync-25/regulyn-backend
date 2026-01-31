package com.regulyn.evidence;

import com.regulyn.events.config.EventsAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.regulyn.evidence", "com.regulyn.common"})
@EntityScan(basePackages = {"com.regulyn.evidence", "com.regulyn.events.outbox"})
@EnableJpaRepositories(basePackages = {"com.regulyn.evidence", "com.regulyn.events.outbox"})
@Import(EventsAutoConfiguration.class)
public class EvidenceReportingServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(EvidenceReportingServiceApplication.class, args);
  }
}
