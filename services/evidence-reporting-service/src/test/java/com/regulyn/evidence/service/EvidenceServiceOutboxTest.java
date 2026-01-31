package com.regulyn.evidence.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.evidence.entity.EvidenceRecord;
import com.regulyn.evidence.model.EvidenceRequest;
import com.regulyn.evidence.model.EvidenceResponse;
import com.regulyn.evidence.repository.EvidenceRecordRepository;
import com.regulyn.events.outbox.OutboxEvent;
import com.regulyn.events.outbox.OutboxEventRepository;
import com.regulyn.events.outbox.OutboxStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Transactional
class EvidenceServiceOutboxTest {

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
        registry.add("spring.flyway.schemas", () -> "evidence");
        registry.add("regulyn.outbox.relay.enabled", () -> "false");
    }

    @Autowired
    private EvidenceService evidenceService;

    @Autowired
    private EvidenceRecordRepository evidenceRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    private UUID testTenantId;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();
        testUserId = UUID.randomUUID();

        TenantContext context = new TenantContext();
        context.setTenantId(testTenantId);
        context.setUserId(testUserId);
        TenantContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void submitEvidence_shouldPersistEvidenceAndWriteToOutbox() {
        // Arrange
        EvidenceRequest request = new EvidenceRequest();
        request.setEvidenceType("SCREENSHOT");
        request.setDescription("Test evidence submission");

        // Act
        EvidenceResponse response = evidenceService.submitEvidence(request);

        // Assert - verify evidence record was created
        assertNotNull(response.getEvidenceId());
        assertNotNull(response.getEvidenceHash());

        List<EvidenceRecord> records = evidenceRepository.findAll();
        assertEquals(1, records.size());
        assertEquals(response.getEvidenceId(), records.get(0).getEvidenceId());

        // Assert - verify outbox event was created
        List<OutboxEvent> outboxEvents = outboxRepository.findAll();
        assertEquals(1, outboxEvents.size());
        
        OutboxEvent event = outboxEvents.get(0);
        assertEquals("evidence.created", event.getEventType());
        assertEquals("evidence-reporting-service", event.getSourceService());
        assertEquals("EVIDENCE", event.getEntityType());
        assertEquals(response.getEvidenceId(), event.getEntityId());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertNotNull(event.getPayload());
        assertTrue(event.getPayload().contains("evidenceId"));
        assertTrue(event.getPayload().contains(response.getEvidenceId()));
    }
}
