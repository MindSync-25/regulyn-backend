package com.regulyn.guardian;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.regulyn.guardian", "com.regulyn.auth", "com.regulyn.observability"})
@EnableScheduling
public class GuardianServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GuardianServiceApplication.class, args);
    }
}
