package com.regulyn.ropa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.regulyn")
@EntityScan(basePackages = {"com.regulyn.ropa", "com.regulyn.events.outbox"})
@EnableJpaRepositories(basePackages = {"com.regulyn.ropa", "com.regulyn.events.outbox"})
public class RopaInventoryServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(RopaInventoryServiceApplication.class, args);
  }
}
