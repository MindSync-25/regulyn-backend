package io.regulyn.identity;

import com.regulyn.auth.config.SecurityConfig;
import com.regulyn.events.config.EventsAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"io.regulyn.identity", "com.regulyn.auth", "com.regulyn.common", "com.regulyn.observability"})
@EntityScan(basePackages = {"io.regulyn.identity.entity", "com.regulyn.events.outbox"})
@EnableJpaRepositories(basePackages = {"io.regulyn.identity.repository", "com.regulyn.events.outbox"})
@Import({EventsAutoConfiguration.class, SecurityConfig.class})
public class IdentityTenantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityTenantServiceApplication.class, args);
    }
}
