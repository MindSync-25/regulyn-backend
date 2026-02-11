package com.regulyn.nominee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "com.regulyn.nominee",
        "com.regulyn.events"
})
@EntityScan(basePackages = {
        "com.regulyn.nominee.entity",
        "com.regulyn.events.outbox"
})
@EnableJpaRepositories(basePackages = {
        "com.regulyn.nominee.repository",
        "com.regulyn.events.outbox"
})
public class NomineeServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(NomineeServiceApplication.class, args);
  }
}
