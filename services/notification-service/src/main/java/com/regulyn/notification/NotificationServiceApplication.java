package com.regulyn.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.regulyn")
@EntityScan(basePackages = {
  "com.regulyn.notification.entity",
  "com.regulyn.events.outbox"
})
@EnableJpaRepositories(basePackages = {
  "com.regulyn.notification.repository",
  "com.regulyn.events.outbox"
})
@EnableScheduling
public class NotificationServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(NotificationServiceApplication.class, args);
  }
}
