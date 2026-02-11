package com.regulyn.guardian;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
    "com.regulyn.guardian",
    "com.regulyn.auth",
    "com.regulyn.observability",
    "com.regulyn.common",
    "com.regulyn.events"
})
@EntityScan(basePackages = {
    "com.regulyn.guardian.entity",
    "com.regulyn.events.outbox"
})
@EnableJpaRepositories(basePackages = {
    "com.regulyn.guardian.repository",
    "com.regulyn.events.outbox"
})
@EnableScheduling
public class GuardianServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GuardianServiceApplication.class, args);
    }
}
