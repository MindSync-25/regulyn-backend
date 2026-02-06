package io.regulyn.connector;

import com.regulyn.events.outbox.OutboxEventRepository;
import io.regulyn.connector.model.ConnectorCursorState;
import io.regulyn.connector.model.ConnectorSchedule;
import io.regulyn.connector.model.WebhookEvent;
import io.regulyn.connector.repository.ConnectorCursorStateRepository;
import io.regulyn.connector.repository.ConnectorScheduleRepository;
import io.regulyn.connector.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Round 2 Part 1B: schedules, webhooks, cursor state.
 * Note: @DirtiesContext is used because these tests somehow corrupt the HikariCP pool
 * for subsequent test classes. This forces Spring to create a fresh context after this class.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConnectorRound2Part1BIntegrationTest extends AbstractIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConnectorScheduleRepository scheduleRepository;

    @Autowired
    private WebhookEventRepository webhookRepository;

    @Autowired
    private ConnectorCursorStateRepository cursorRepository;
    
    @Autowired
    private OutboxEventRepository outboxRepository;
    
    @BeforeEach
    void setUp() {
        // Clean up all test data in correct order (respecting FK constraints)
        outboxRepository.deleteAll();
        cursorRepository.deleteAll();
        webhookRepository.deleteAll();
        scheduleRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM connector.connector_runs");
        jdbcTemplate.update("DELETE FROM connector.connector_credentials");
        jdbcTemplate.update("DELETE FROM connector.connectors");
    }

    // Helper method to create a connector entity (parent for FK)
    private UUID createTestConnector(UUID tenantId) {
        UUID connectorId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO connector.connectors (connector_id, tenant_id, connector_name, connector_type, status, auth_type, metadata, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb, NOW(), NOW())",
            connectorId, tenantId, "Test Connector", "REST_API", "ACTIVE", "API_KEY"
        );
        return connectorId;
    }

    @Test
    void testRound2Part1BTablesExist() {
        // Verify connector_schedules table exists
        Integer schedTableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'connector' AND table_name = 'connector_schedules'",
            Integer.class
        );
        assertEquals(1, schedTableCount, "connector_schedules table should exist");

        // Verify webhook_events table exists
        Integer webhookTableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'connector' AND table_name = 'webhook_events'",
            Integer.class
        );
        assertEquals(1, webhookTableCount, "webhook_events table should exist");

        // Verify connector_cursor_state table exists
        Integer cursorTableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'connector' AND table_name = 'connector_cursor_state'",
            Integer.class
        );
        assertEquals(1, cursorTableCount, "connector_cursor_state table should exist");
    }

    @Test
    void testScheduleJobTypeConstraint() {
        UUID tenantId = UUID.randomUUID();
        UUID connectorId = createTestConnector(tenantId);

        // Valid job type should work
        ConnectorSchedule validSchedule = new ConnectorSchedule();
        validSchedule.setTenantId(tenantId);
        validSchedule.setConnectorId(connectorId);
        validSchedule.setJobType(ConnectorSchedule.JobType.AUDIT_PULL);
        validSchedule.setCron("0 0 * * *");
        validSchedule.setNextFireAt(Instant.now().plusSeconds(3600));
        ConnectorSchedule saved = scheduleRepository.save(validSchedule);
        assertNotNull(saved.getId());

        // Invalid job type should fail (test via direct SQL)
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                "INSERT INTO connector.connector_schedules (id, tenant_id, connector_id, job_type, cron, next_fire_at, created_at, updated_at) VALUES (?, ?, ?, 'INVALID_JOB', '0 0 * * *', NOW(), NOW(), NOW())",
                UUID.randomUUID(), tenantId, connectorId
            );
        }, "Invalid job_type should violate CHECK constraint");
    }

    @Test
    void testCursorStateUniqueConstraint() {
        UUID tenantId = UUID.randomUUID();
        UUID connectorId = createTestConnector(tenantId);

        // First cursor state should succeed (global cursor, target_id = NULL)
        ConnectorCursorState cursor1 = new ConnectorCursorState();
        cursor1.setTenantId(tenantId);
        cursor1.setConnectorId(connectorId);
        cursor1.setTargetId(null); // Global cursor
        cursor1.setJobType(ConnectorCursorState.JobType.AUDIT_PULL);
        Map<String, Object> cursorData = new HashMap<>();
        cursorData.put("lastTimestamp", "2026-01-01T00:00:00Z");
        cursor1.setCursorJson(cursorData);
        cursorRepository.save(cursor1);

        // Second cursor with different job_type should succeed (different composite key)
        ConnectorCursorState cursor2 = new ConnectorCursorState();
        cursor2.setTenantId(tenantId);
        cursor2.setConnectorId(connectorId);
        cursor2.setTargetId(null);
        cursor2.setJobType(ConnectorCursorState.JobType.EXPORT); // Different job_type
        cursor2.setCursorJson(cursorData);
        cursorRepository.save(cursor2);

        // Third cursor with same composite key as first should fail via direct SQL
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                "INSERT INTO connector.connector_cursor_state (id, tenant_id, connector_id, target_id, job_type, cursor_json, updated_at) VALUES (?, ?, ?, NULL, 'AUDIT_PULL', '{}'::jsonb, NOW())",
                UUID.randomUUID(), tenantId, connectorId
            );
        }, "Duplicate composite key should violate unique constraint");
    }

    @Test
    void testScheduleFindDueSchedulesReturnsCorrectOrder() {
        UUID tenantId = UUID.randomUUID();
        UUID connectorId = createTestConnector(tenantId);

        Instant now = Instant.now();

        // Create schedule due in the past (should be returned first)
        ConnectorSchedule pastDue = new ConnectorSchedule();
        pastDue.setTenantId(tenantId);
        pastDue.setConnectorId(connectorId);
        pastDue.setJobType(ConnectorSchedule.JobType.AUDIT_PULL);
        pastDue.setCron("0 0 * * *");
        pastDue.setNextFireAt(now.minusSeconds(3600));
        scheduleRepository.save(pastDue);

        // Create schedule due now (should be returned second)
        ConnectorSchedule dueNow = new ConnectorSchedule();
        dueNow.setTenantId(tenantId);
        dueNow.setConnectorId(connectorId);
        dueNow.setJobType(ConnectorSchedule.JobType.EXPORT);
        dueNow.setCron("0 0 * * *");
        dueNow.setNextFireAt(now.minusSeconds(60));
        scheduleRepository.save(dueNow);

        // Create schedule due in the future (should NOT be returned)
        ConnectorSchedule futureSchedule = new ConnectorSchedule();
        futureSchedule.setTenantId(tenantId);
        futureSchedule.setConnectorId(connectorId);
        futureSchedule.setJobType(ConnectorSchedule.JobType.AUDIT_PULL);
        futureSchedule.setCron("0 0 * * *");
        futureSchedule.setNextFireAt(now.plusSeconds(3600));
        scheduleRepository.save(futureSchedule);

        // Create disabled schedule (should NOT be returned even if due)
        ConnectorSchedule disabledSchedule = new ConnectorSchedule();
        disabledSchedule.setTenantId(tenantId);
        disabledSchedule.setConnectorId(connectorId);
        disabledSchedule.setJobType(ConnectorSchedule.JobType.EXPORT);
        disabledSchedule.setCron("0 0 * * *");
        disabledSchedule.setEnabled(false);
        disabledSchedule.setNextFireAt(now.minusSeconds(7200));
        scheduleRepository.save(disabledSchedule);

        List<ConnectorSchedule> dueSchedules = scheduleRepository.findDueSchedules(now);

        assertEquals(2, dueSchedules.size(), "Should return 2 due schedules");
        assertTrue(dueSchedules.get(0).getNextFireAt().isBefore(dueSchedules.get(1).getNextFireAt()),
            "Schedules should be ordered by nextFireAt ascending");
    }

    @Test
    void testWebhookPayloadHashLengthValidation() {
        UUID tenantId = UUID.randomUUID();
        UUID connectorId = createTestConnector(tenantId);

        // Valid 64-char hash should work
        WebhookEvent validWebhook = new WebhookEvent();
        validWebhook.setTenantId(tenantId);
        validWebhook.setConnectorId(connectorId);
        validWebhook.setProvider("GitHub");
        validWebhook.setSignatureValid(true);
        validWebhook.setPayloadHash("a".repeat(64)); // 64-char hex
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "push");
        validWebhook.setRawPayloadJson(payload);
        validWebhook.setCorrelationId("test-correlation-id");
        WebhookEvent saved = webhookRepository.save(validWebhook);
        assertNotNull(saved.getId());

        // Hash longer than 64 chars should fail (VARCHAR(64) constraint)
        WebhookEvent invalidWebhook = new WebhookEvent();
        invalidWebhook.setTenantId(tenantId);
        invalidWebhook.setConnectorId(connectorId);
        invalidWebhook.setProvider("GitHub");
        invalidWebhook.setSignatureValid(true);
        invalidWebhook.setPayloadHash("a".repeat(65)); // 65 chars - too long
        invalidWebhook.setRawPayloadJson(payload);
        invalidWebhook.setCorrelationId("test-correlation-id-2");

        assertThrows(DataIntegrityViolationException.class, () -> {
            webhookRepository.saveAndFlush(invalidWebhook);
        }, "Payload hash longer than 64 chars should fail");
    }

    @Test
    void testWebhookFindByCorrelationId() {
        UUID tenantId = UUID.randomUUID();
        UUID connectorId = createTestConnector(tenantId);

        String correlationId = "unique-correlation-" + UUID.randomUUID();

        WebhookEvent webhook = new WebhookEvent();
        webhook.setTenantId(tenantId);
        webhook.setConnectorId(connectorId);
        webhook.setProvider("Slack");
        webhook.setSignatureValid(true);
        webhook.setPayloadHash("b".repeat(64));
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "message");
        webhook.setRawPayloadJson(payload);
        webhook.setCorrelationId(correlationId);
        webhook.setNormalizedType("message.sent");
        webhookRepository.save(webhook);

        var found = webhookRepository.findByTenantIdAndCorrelationId(tenantId, correlationId);
        assertTrue(found.isPresent(), "Should find webhook by correlation ID");
        assertEquals("Slack", found.get().getProvider());
        assertEquals("message.sent", found.get().getNormalizedType());
    }

    @Test
    void testWebhookFindRecentByConnector() {
        UUID tenantId = UUID.randomUUID();
        UUID connectorId = createTestConnector(tenantId);

        // Create 3 webhooks with different received times
        for (int i = 0; i < 3; i++) {
            WebhookEvent webhook = new WebhookEvent();
            webhook.setTenantId(tenantId);
            webhook.setConnectorId(connectorId);
            webhook.setProvider("Provider-" + i);
            webhook.setSignatureValid(true);
            webhook.setPayloadHash("c".repeat(64));
            Map<String, Object> payload = new HashMap<>();
            payload.put("index", i);
            webhook.setRawPayloadJson(payload);
            webhook.setCorrelationId("correlation-" + i);
            webhookRepository.save(webhook);
            try {
                Thread.sleep(10); // Ensure different received_at timestamps
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        List<WebhookEvent> recent = webhookRepository.findByTenantIdAndConnectorIdOrderByReceivedAtDesc(
            tenantId, connectorId, PageRequest.of(0, 10)
        );

        assertEquals(3, recent.size(), "Should find all webhooks for connector");
        assertEquals("Provider-2", recent.get(0).getProvider(), "Most recent should be first");
        assertEquals("Provider-0", recent.get(2).getProvider(), "Oldest should be last");
    }
}
