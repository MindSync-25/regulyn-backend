package com.regulyn.dsar.sla;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.regulyn.dsar.entity.DsarEscalationTaskEntity;
import com.regulyn.dsar.entity.DsarRequestEntity;
import com.regulyn.dsar.entity.EscalationStatus;
import com.regulyn.dsar.entity.EscalationThreshold;
import com.regulyn.dsar.repository.DsarEscalationTaskRepository;
import com.regulyn.dsar.repository.DsarRequestRepository;
import com.regulyn.dsar.service.DsarSlaEscalationScheduler;
import com.regulyn.events.outbox.OutboxEventRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
/**
 * This was formerly *IT; renamed to *Test so Surefire always runs it.
 */
class DsarSlaEscalationSchedulerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    private static WireMockServer wireMockServer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("dsar.sla.scheduler.enabled", () -> "false");
        registry.add("dsar.sla.escalation.enabled", () -> "true");
        registry.add("dsar.sla.escalation.recipients.emails", () -> "dpo@example.com,admin@example.com");
        registry.add("notification.service.url", () -> "http://localhost:" + wireMockServer.port());
    }

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @Autowired
    private DsarSlaEscalationScheduler escalationScheduler;

    @Autowired
    private DsarRequestRepository dsarRequestRepository;

    @Autowired
    private DsarEscalationTaskRepository escalationTaskRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();

        wireMockServer.resetAll();
        outboxEventRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM dsar.audit_events");
        escalationTaskRepository.deleteAll();
        dsarRequestRepository.deleteAll();
    }

    @Test
    void createsTasksOnceAndDoesNotDuplicate() {
        DsarRequestEntity dsar = createDsar("RECEIVED", Instant.now().minus(61, ChronoUnit.DAYS));
        UUID dsarId = dsar.getRequestIdPk();

        stubFor(post(urlEqualTo("/api/notifications/send"))
            .willReturn(okJson("{\"notificationRequestId\":\"req-60\"}")));

        escalationScheduler.checkSlaEscalations();
        escalationScheduler.checkSlaEscalations();

        DsarEscalationTaskEntity task = escalationTaskRepository
            .findByTenantIdAndDsarIdAndThreshold(tenantId, dsarId, EscalationThreshold.D60)
            .orElseThrow();

        assertTaskIntegrity(task, dsar.getCreatedAt());
        assertEquals(EscalationStatus.NOTIFICATION_QUEUED, task.getStatus());
        assertEquals("req-60", task.getNotificationRequestId());

        assertAuditEventCount("DSAR_SLA_THRESHOLD_REACHED", 1);
        assertAuditEventCount("DSAR_ESCALATION_TASK_CREATED", 1);
        assertAuditEventCount("DSAR_ESCALATION_NOTIFICATION_QUEUED", 1);

        assertOutboxEventCount("dsar.sla_threshold_reached", 1);
        assertOutboxEventCount("dsar.escalation_task_created", 1);
        assertOutboxEventCount("dsar.escalation_notification_queued", 1);

        verify(1, postRequestedFor(urlEqualTo("/api/notifications/send")));
    }

    @Test
    void notificationQueuedStoresRefs() {
        DsarRequestEntity dsar = createDsar("RECEIVED", Instant.now().minus(81, ChronoUnit.DAYS));
        UUID dsarId = dsar.getRequestIdPk();

        stubFor(post(urlEqualTo("/api/notifications/send"))
            .withRequestBody(matchingJsonPath("$.requestRef", containing("D80")))
            .willReturn(okJson("{\"notificationRequestId\":\"req-80\",\"providerMessageId\":\"msg-80\"}")));

        stubFor(post(urlEqualTo("/api/notifications/send"))
            .withRequestBody(matchingJsonPath("$.requestRef", containing("D60")))
            .willReturn(okJson("{\"notificationRequestId\":\"req-60\"}")));

        escalationScheduler.checkSlaEscalations();

        DsarEscalationTaskEntity task = escalationTaskRepository
            .findByTenantIdAndDsarIdAndThreshold(tenantId, dsarId, EscalationThreshold.D80)
            .orElseThrow();

        assertTaskIntegrity(task, dsar.getCreatedAt());
        assertEquals(EscalationStatus.NOTIFICATION_SENT, task.getStatus());
        assertEquals("req-80", task.getNotificationRequestId());
        assertEquals("msg-80", task.getProviderMessageId());

        assertAuditEventCount("DSAR_ESCALATION_NOTIFICATION_QUEUED", 2);
        assertAuditEventCount("DSAR_ESCALATION_NOTIFICATION_SENT", 1);

        assertOutboxEventCount("dsar.escalation_notification_queued", 2);
        assertOutboxEventCount("dsar.escalation_notification_sent", 1);
    }

    @Test
    void closedDsarDoesNotEscalate() {
        createDsar("CLOSED", Instant.now().minus(81, ChronoUnit.DAYS));

        escalationScheduler.checkSlaEscalations();

        assertEquals(0, escalationTaskRepository.count());
        assertEquals(0, outboxEventRepository.count());
        assertAuditEventCount("DSAR_SLA_THRESHOLD_REACHED", 0);
    }

    @Test
    void notificationFailureIsRetryable() {
        DsarRequestEntity dsar = createDsar("RECEIVED", Instant.now().minus(61, ChronoUnit.DAYS));
        UUID dsarId = dsar.getRequestIdPk();

        stubFor(post(urlEqualTo("/api/notifications/send"))
            .willReturn(serverError()));

        escalationScheduler.checkSlaEscalations();

        DsarEscalationTaskEntity task = escalationTaskRepository
            .findByTenantIdAndDsarIdAndThreshold(tenantId, dsarId, EscalationThreshold.D60)
            .orElseThrow();

        assertTaskIntegrity(task, dsar.getCreatedAt());
        assertEquals(EscalationStatus.CREATED, task.getStatus());
        assertNotNull(task.getLastError());
        assertNull(task.getNotificationRequestId());

        assertAuditEventCount("DSAR_SLA_THRESHOLD_REACHED", 1);
        assertAuditEventCount("DSAR_ESCALATION_TASK_CREATED", 1);
        assertAuditEventCount("DSAR_ESCALATION_NOTIFICATION_QUEUED", 0);

        assertOutboxEventCount("dsar.sla_threshold_reached", 1);
        assertOutboxEventCount("dsar.escalation_task_created", 1);
        assertOutboxEventCount("dsar.escalation_notification_queued", 0);

        stubFor(post(urlEqualTo("/api/notifications/send"))
            .willReturn(okJson("{\"notificationRequestId\":\"req-retry\"}")));

        escalationScheduler.checkSlaEscalations();

        DsarEscalationTaskEntity retried = escalationTaskRepository
            .findByTenantIdAndDsarIdAndThreshold(tenantId, dsarId, EscalationThreshold.D60)
            .orElseThrow();

        assertTaskIntegrity(retried, dsar.getCreatedAt());
        assertEquals(task.getId(), retried.getId());
        assertEquals(EscalationStatus.NOTIFICATION_QUEUED, retried.getStatus());
        assertEquals("req-retry", retried.getNotificationRequestId());

        assertAuditEventCount("DSAR_ESCALATION_NOTIFICATION_QUEUED", 1);
        assertOutboxEventCount("dsar.escalation_notification_queued", 1);

        verify(2, postRequestedFor(urlEqualTo("/api/notifications/send")));
    }

    private DsarRequestEntity createDsar(String status, Instant createdAt) {
        DsarRequestEntity entity = new DsarRequestEntity();
        entity.setTenantId(tenantId);
        entity.setRequestId(UUID.randomUUID().toString());
        entity.setRequestType("ACCESS");
        entity.setStatus(status);
        entity.setRequesterEmail("test@example.com");
        entity.setDataPrincipalId(UUID.randomUUID());
        entity.setCreatedBy(userId);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(createdAt);
        entity.setDueAt(createdAt.plus(90, ChronoUnit.DAYS));
        if ("CLOSED".equals(status)) {
            entity.setClosedAt(Instant.now());
        }
        return dsarRequestRepository.saveAndFlush(entity);
    }

    private void assertTaskIntegrity(DsarEscalationTaskEntity task, Instant dsarCreatedAt) {
        int expectedDays = switch (task.getThreshold()) {
            case D60 -> 60;
            case D80 -> 80;
            case D90 -> 90;
        };

        assertEquals(expectedDays, task.getThresholdDays());
        Instant expectedDueAt = dsarCreatedAt.plus(expectedDays, ChronoUnit.DAYS);
        assertEquals(expectedDueAt.truncatedTo(ChronoUnit.MILLIS), task.getDueAt().truncatedTo(ChronoUnit.MILLIS));

        if (task.getStatus() == EscalationStatus.CREATED || task.getStatus() == EscalationStatus.SUPPRESSED_CLOSED) {
            assertNull(task.getNotificationRequestId());
        } else {
            assertNotNull(task.getNotificationRequestId());
        }
    }

    private void assertAuditEventCount(String action, int expected) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dsar.audit_events WHERE action = ?",
            Integer.class, action);
        assertNotNull(count);
        assertEquals(expected, count.intValue());
    }

    private void assertOutboxEventCount(String eventType, int expected) {
        long count = outboxEventRepository.findAll().stream()
            .filter(event -> eventType.equals(event.getEventType()))
            .count();
        assertEquals(expected, count);
    }
}
