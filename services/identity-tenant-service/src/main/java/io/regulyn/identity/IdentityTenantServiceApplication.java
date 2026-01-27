package io.regulyn.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"io.regulyn.identity", "com.regulyn.auth", "com.regulyn.common", "com.regulyn.observability"})
public class IdentityTenantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityTenantServiceApplication.class, args);
    }
}
