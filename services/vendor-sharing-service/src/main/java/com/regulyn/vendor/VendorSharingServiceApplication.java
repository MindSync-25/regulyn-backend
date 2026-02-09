package com.regulyn.vendor;

import com.regulyn.events.config.EventsAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {
  "com.regulyn.vendor",
  "com.regulyn.common.audit"
})
@EnableScheduling
@Import(EventsAutoConfiguration.class)
@EntityScan(basePackages = {
  "com.regulyn.vendor.model",
  "com.regulyn.events.outbox"
})
@EnableJpaRepositories(basePackages = {
  "com.regulyn.vendor.repository",
  "com.regulyn.events.outbox"
})
public class VendorSharingServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(VendorSharingServiceApplication.class, args);
  }
}
