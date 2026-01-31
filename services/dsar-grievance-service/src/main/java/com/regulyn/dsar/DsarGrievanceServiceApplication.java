package com.regulyn.dsar;

import com.regulyn.events.config.EventsAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {"com.regulyn.dsar", "com.regulyn.events.outbox"})
@EnableJpaRepositories(basePackages = {"com.regulyn.dsar", "com.regulyn.events.outbox"})
@Import(EventsAutoConfiguration.class)
public class DsarGrievanceServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(DsarGrievanceServiceApplication.class, args);
  }
}
