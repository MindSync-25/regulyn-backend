package com.regulyn.incident.workflow;

import com.regulyn.incident.entity.IncidentCase;
import com.regulyn.incident.repository.IncidentCaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class IncidentWorkflowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("incident")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private IncidentCaseRepository incidentCaseRepository;

    @Test
    public void testIncidentWorkflow() {
        IncidentCase incident = new IncidentCase();
        incident.setTenantId(UUID.randomUUID());
        incident.setIncidentType("DATA_BREACH");
        incident.setSeverity("HIGH");
        incident.setStatus("OPEN");

        IncidentCase saved = incidentCaseRepository.save(incident);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getNotifyDueAt()).isNotNull();
        assertThat(saved.getNotifyDueAt()).isAfter(Instant.now());
        assertThat(saved.getOpenedAt()).isNotNull();
    }
}
