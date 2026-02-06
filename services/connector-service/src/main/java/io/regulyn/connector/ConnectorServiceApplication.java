package io.regulyn.connector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"io.regulyn.connector", "com.regulyn.auth", "com.regulyn.common", "com.regulyn.events", "com.regulyn.observability"})
@EnableJpaRepositories(basePackages = {"io.regulyn.connector", "com.regulyn.auth", "com.regulyn.events"})
@EntityScan(basePackages = {"io.regulyn.connector", "com.regulyn.auth", "com.regulyn.events"})
public class ConnectorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConnectorServiceApplication.class, args);
    }
}
