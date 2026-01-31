package com.regulyn.dsar.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.dsar.entity.DsarRequestEntity;
import com.regulyn.dsar.model.DsarRequest;
import com.regulyn.dsar.model.DsarResponse;
import com.regulyn.dsar.repository.DsarRequestRepository;
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
class DsarServiceOutboxTest {

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
        registry.add("spring.flyway.schemas", () -> "dsar");
        registry.add("regulyn.outbox.relay.enabled", () -> "false");
    }

    @Autowired
    private DsarService dsarService;

    @Autowired
    private DsarRequestRepository dsarRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @BeforeEach
    void setUp() {
        // Set up tenant context for testing
        TenantContext context = new TenantContext();
        context.setTenantId(UUID.randomUUID());
        context.setUserId(UUID.randomUUID());
        TenantContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void submitRequest_shouldPersistDsarAndWriteToOutbox() {
        // Arrange
        DsarRequest request = new DsarRequest();
        request.setRequestType("ACCESS");
        request.setRequesterEmail("user@test.com");

        // Act
        DsarResponse response = dsarService.submitRequest(request);

        // Assert - verify DSAR request was created
        assertNotNull(response.getRequestId());
        assertEquals("RECEIVED", response.getStatus());

        List<DsarRequestEntity> requests = dsarRepository.findAll();
        assertEquals(1, requests.size());
        assertEquals(response.getRequestId(), requests.get(0).getRequestId());

        // Assert - verify outbox event was created
        List<OutboxEvent> outboxEvents = outboxRepository.findAll();
        assertEquals(1, outboxEvents.size());
        
        OutboxEvent event = outboxEvents.get(0);
        assertEquals("dsar.created", event.getEventType());
        assertEquals("dsar-grievance-service", event.getSourceService());
        assertEquals("DSAR", event.getEntityType());
        assertEquals(response.getRequestId(), event.getEntityId());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertNotNull(event.getPayload());
        assertTrue(event.getPayload().contains("requestId"));
        assertTrue(event.getPayload().contains(response.getRequestId()));
    }
}
