package com.regulyn.employee;

import com.regulyn.events.config.EventsAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
    "com.regulyn.employee",
    "com.regulyn.common.audit",
    "com.regulyn.auth",
    "com.regulyn.events",
    "com.regulyn.observability"
})
@EntityScan(basePackages = {"com.regulyn.employee", "com.regulyn.events.outbox"})
@EnableJpaRepositories(basePackages = {"com.regulyn.employee", "com.regulyn.events.outbox"})
@Import(EventsAutoConfiguration.class)
@EnableScheduling
public class EmployeeDataServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(EmployeeDataServiceApplication.class, args);
  }
}
