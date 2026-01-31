package com.regulyn.retention.workflow;

import com.regulyn.retention.entity.DeletionRequest;
import com.regulyn.retention.repository.DeletionRequestRepository;
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
public class DeletionWorkflowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("deletion")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DeletionRequestRepository deletionRequestRepository;

    @Test
    public void testDeletionRequestWorkflow() {
        DeletionRequest request = new DeletionRequest();
        request.setTenantId(UUID.randomUUID());
        request.setSubjectId(UUID.randomUUID());
        request.setSubjectType("USER");
        request.setEntityType("CUSTOMER");
        request.setSource("ADMIN");
        request.setStatus("REQUESTED");

        DeletionRequest saved = deletionRequestRepository.save(request);

        assertThat(saved.getDeletionId()).isNotNull();
        assertThat(saved.getDueAt()).isNotNull();
        assertThat(saved.getDueAt()).isAfter(Instant.now());
    }
}
