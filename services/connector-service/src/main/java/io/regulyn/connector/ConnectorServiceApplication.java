package io.regulyn.connector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"io.regulyn.connector", "com.regulyn.auth", "com.regulyn.common", "com.regulyn.observability"})
public class ConnectorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConnectorServiceApplication.class, args);
    }
}
