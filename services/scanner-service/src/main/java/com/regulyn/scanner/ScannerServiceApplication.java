package com.regulyn.scanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.regulyn.scanner", "io.regulyn.scanner", "com.regulyn.common", "com.regulyn.events", "com.regulyn.auth"})
@EnableJpaRepositories(basePackages = {"io.regulyn.scanner.repository", "com.regulyn.events.outbox"})
@EntityScan(basePackages = {"io.regulyn.scanner.model", "com.regulyn.events.outbox"})
public class ScannerServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(ScannerServiceApplication.class, args);
  }
}
