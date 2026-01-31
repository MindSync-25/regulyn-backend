package com.regulyn.dsar.workflow;

import com.regulyn.dsar.DsarGrievanceServiceApplication;
import com.regulyn.dsar.entity.DsarRequestEntity;
import com.regulyn.dsar.repository.DsarRequestRepository;
import com.regulyn.events.outbox.OutboxEvent;
import com.regulyn.events.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DsarGrievanceServiceApplication.class)
@Testcontainers
public class DsarWorkflowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DsarRequestRepository dsarRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Test
    void testDsarWorkflowCreation() {
        // Given
        UUID tenantId = UUID.randomUUID();
        String requestId = "DSAR-TEST-001";

        // When
        DsarRequestEntity request = new DsarRequestEntity();
        request.setTenantId(tenantId);
        request.setRequestId(requestId);
        request.setRequestType("ACCESS");
        request.setStatus("RECEIVED");
        request.setRequesterEmail("test@example.com");
        request.setDataPrincipalId(UUID.randomUUID());

        DsarRequestEntity saved = dsarRepository.save(request);

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.getRequestIdPk()).isNotNull();
        assertThat(saved.getRequestId()).isEqualTo(requestId);
        assertThat(saved.getDueAt()).isNotNull(); // Auto-set to 90 days

        // Verify database persistence
        DsarRequestEntity found = dsarRepository.findById(saved.getRequestIdPk()).orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getStatus()).isEqualTo("RECEIVED");
        assertThat(found.getDueAt()).isNotNull();

        // Verify outbox table exists (migration ran successfully)
        List<OutboxEvent> outboxEvents = outboxRepository.findAll();
        // Outbox events will be written by service layer, not directly in this test
        // This test just verifies the database schema is correctly set up
        assertThat(outboxEvents).isNotNull();
    }
}
