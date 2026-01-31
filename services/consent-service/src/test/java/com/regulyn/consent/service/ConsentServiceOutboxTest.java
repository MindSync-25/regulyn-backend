package com.regulyn.consent.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.consent.entity.ConsentRecord;
import com.regulyn.consent.model.ConsentRequest;
import com.regulyn.consent.model.ConsentResponse;
import com.regulyn.consent.repository.ConsentRecordRepository;
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
class ConsentServiceOutboxTest {

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
        registry.add("spring.flyway.schemas", () -> "consent");
        registry.add("regulyn.outbox.relay.enabled", () -> "false");
    }

    @Autowired
    private ConsentService consentService;

    @Autowired
    private ConsentRecordRepository consentRepository;

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
    void createConsent_shouldPersistConsentAndWriteToOutbox() {
        // Arrange
        ConsentRequest request = new ConsentRequest();
        request.setUserId("test-user-123");
        request.setPurpose("marketing");
        request.setLanguage("en");
        request.setNoticeText("This is a test consent notice");
        request.setSource("web");

        // Act
        ConsentResponse response = consentService.createConsent(request);

        // Assert - verify consent record was created
        assertNotNull(response.getReceiptId());
        assertNotNull(response.getHash());

        List<ConsentRecord> records = consentRepository.findAll();
        assertEquals(1, records.size());
        assertEquals(response.getReceiptId(), records.get(0).getReceiptId());

        // Assert - verify outbox event was created
        List<OutboxEvent> outboxEvents = outboxRepository.findAll();
        assertEquals(1, outboxEvents.size());
        
        OutboxEvent event = outboxEvents.get(0);
        assertEquals("consent.created", event.getEventType());
        assertEquals("consent-service", event.getSourceService());
        assertEquals("CONSENT", event.getEntityType());
        assertEquals(response.getReceiptId(), event.getEntityId());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertNotNull(event.getPayload());
        assertTrue(event.getPayload().contains("receiptId"));
        assertTrue(event.getPayload().contains(response.getReceiptId()));
    }
}
