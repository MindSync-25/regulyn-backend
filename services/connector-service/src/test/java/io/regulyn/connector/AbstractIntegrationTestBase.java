package io.regulyn.connector;

import io.regulyn.connector.config.TestConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for all connector-service integration tests.
 * Provides shared Testcontainers setup for PostgreSQL.
 * 
 * <p>All integration tests should extend this class to ensure consistent container lifecycle
 * and avoid "Container needs to be initialized" errors when running the full test suite.</p>
 * 
 * <p>Containers are static and shared across all test classes to avoid Docker resource exhaustion.</p>
 */
@SpringBootTest
@Import(TestConfig.class)
public abstract class AbstractIntegrationTestBase {

    /**
     * Shared PostgreSQL container for all integration tests.
     * Static to ensure it starts once per JVM and is reused across test classes.
     */
    protected static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("connector_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(false) // Disable reuse to avoid stale connection issues
                .withStartupTimeout(java.time.Duration.ofMinutes(5)); // Increase startup timeout

        postgres.start();
        Runtime.getRuntime().addShutdownHook(new Thread(postgres::stop));
    }

    /**
     * Configure Spring Boot datasource properties from Testcontainers.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL connection
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        
        // Flyway migration
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        
        // Disable scheduled tasks in tests to avoid background interference
        registry.add("spring.task.scheduling.enabled", () -> "false");

        // Encryption key for credential tests
        registry.add("connector.credentials.encryption.key", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        
        // HikariCP connection pool - increased for full test suite
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "2");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "60000");
        registry.add("spring.datasource.hikari.idle-timeout", () -> "300000");
        registry.add("spring.datasource.hikari.max-lifetime", () -> "600000");
        registry.add("spring.datasource.hikari.leak-detection-threshold", () -> "60000");
        
        // Evidence service (mock endpoint)
        registry.add("regulyn.evidence.baseUrl", () -> "http://localhost:9999");
        registry.add("evidence.baseUrl", () -> "http://localhost:9999");
    }
}
