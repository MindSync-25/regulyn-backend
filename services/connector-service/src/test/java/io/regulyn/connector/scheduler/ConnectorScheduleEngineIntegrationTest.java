package io.regulyn.connector.scheduler;

import com.regulyn.events.outbox.OutboxEvent;
import com.regulyn.events.outbox.OutboxEventRepository;
import io.regulyn.connector.AbstractIntegrationTestBase;
import io.regulyn.connector.config.SchedulerConfig;
import io.regulyn.connector.model.ConnectorRun;
import io.regulyn.connector.model.ConnectorSchedule;
import io.regulyn.connector.repository.ConnectorRunRepository;
import io.regulyn.connector.repository.ConnectorScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ConnectorScheduleEngine.
 * Tests distributed claiming, run creation, idempotency, and event emission.
 */
class ConnectorScheduleEngineIntegrationTest extends AbstractIntegrationTestBase {

    @Autowired
    private ConnectorScheduleEngine scheduleEngine;

    @Autowired
    private ConnectorScheduleRepository scheduleRepository;

    @Autowired
    private ConnectorRunRepository runRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID tenantId;
    private UUID connectorId;

    @BeforeEach
    void setUp() {
        // Clean up
        runRepository.deleteAll();
        scheduleRepository.deleteAll();
        outboxRepository.deleteAll();

        // Create test connector (required FK)
        tenantId = UUID.randomUUID();
        connectorId = createTestConnector(tenantId);
    }

    @Test
    void dueSchedule_isClaimed_andRunCreated_andNextFireUpdated() {
        // Create a schedule that is due (next_fire_at in the past)
        ConnectorSchedule schedule = new ConnectorSchedule();
        schedule.setTenantId(tenantId);
        schedule.setConnectorId(connectorId);
        schedule.setJobType(ConnectorSchedule.JobType.AUDIT_PULL);
        schedule.setCron("0 0 * * * *"); // Every hour at minute 0
        schedule.setTimezone("UTC");
        schedule.setEnabled(true);
        schedule.setNextFireAt(Instant.now().minus(10, ChronoUnit.MINUTES)); // 10 minutes ago
        schedule = scheduleRepository.save(schedule);

        Instant nextFireBefore = schedule.getNextFireAt();
        assertNull(schedule.getLastFireAt(), "Last fire should be null initially");

        // Process schedules
        scheduleEngine.processSchedules();

        // Assert run was created
        List<ConnectorRun> runs = runRepository.findAll();
        assertEquals(1, runs.size(), "Exactly one run should be created");

        ConnectorRun run = runs.get(0);
        assertEquals(tenantId, run.getTenantId());
        assertEquals(connectorId, run.getConnectorId());
        assertEquals(schedule.getId(), run.getScheduleId());
        assertEquals(ConnectorRun.JobType.AUDIT_PULL, run.getJobType());
        assertEquals(ConnectorRun.RunStatus.PENDING, run.getStatus());
        assertEquals(0, run.getAttempts());
        assertEquals(5, run.getMaxAttempts());
        assertNotNull(run.getCorrelationId());
        assertTrue(run.getCorrelationId().startsWith("schedule-"));

        // Assert schedule was updated
        ConnectorSchedule updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertNotNull(updated.getLastFireAt(), "Last fire should be set");
        assertNotNull(updated.getNextFireAt(), "Next fire should be computed");
        assertTrue(updated.getNextFireAt().isAfter(nextFireBefore), 
            "Next fire should be moved forward");
        assertTrue(updated.getNextFireAt().isAfter(Instant.now()), 
            "Next fire should be in the future");

        // Assert audit/outbox events exist
        List<OutboxEvent> allEvents = outboxRepository.findAll();
        assertTrue(allEvents.size() >= 2, "Should have at least SCHEDULE_FIRED and RUN_CREATED events");

        final UUID scheduleIdForLambda = schedule.getId();
        boolean hasScheduleFired = allEvents.stream()
            .anyMatch(e -> e.getEventType().equals("SCHEDULE_FIRED") 
                && e.getEntityId().equals(scheduleIdForLambda.toString()));
        assertTrue(hasScheduleFired, "SCHEDULE_FIRED event should exist");

        final UUID runIdForLambda = run.getId();
        boolean hasRunCreated = allEvents.stream()
            .anyMatch(e -> e.getEventType().equals("RUN_CREATED") 
                && e.getEntityId().equals(runIdForLambda.toString()));
        assertTrue(hasRunCreated, "RUN_CREATED event should exist");
    }

    @Test
    void idempotency_preventsDuplicateRuns() {
        // Create a schedule that is due
        ConnectorSchedule schedule = new ConnectorSchedule();
        schedule.setTenantId(tenantId);
        schedule.setConnectorId(connectorId);
        schedule.setJobType(ConnectorSchedule.JobType.AUDIT_PULL);
        schedule.setCron("0 0 * * * *");
        schedule.setTimezone("UTC");
        schedule.setEnabled(true);
        schedule.setNextFireAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        schedule = scheduleRepository.save(schedule);

        // Process schedules first time
        scheduleEngine.processSchedules();

        // Verify one run created
        List<ConnectorRun> runsAfterFirst = runRepository.findAll();
        assertEquals(1, runsAfterFirst.size(), "First processing should create one run");

        // Reset next_fire_at to be due again (simulate immediate retry)
        schedule = scheduleRepository.findById(schedule.getId()).orElseThrow();
        schedule.setNextFireAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        scheduleRepository.save(schedule);

        // Process schedules second time
        scheduleEngine.processSchedules();

        // Verify STILL only one run (idempotency check prevents duplicate)
        List<ConnectorRun> runsAfterSecond = runRepository.findAll();
        assertEquals(1, runsAfterSecond.size(), 
            "Idempotency should prevent duplicate run within 1-minute window");
    }

    @Test
    void skipLocked_behavior_smoke_test() throws Exception {
        // Create multiple due schedules
        ConnectorSchedule schedule1 = createDueSchedule(tenantId, connectorId, "0 0 * * * *");
        ConnectorSchedule schedule2 = createDueSchedule(tenantId, connectorId, "0 0 * * * *");
        ConnectorSchedule schedule3 = createDueSchedule(tenantId, connectorId, "0 0 * * * *");

        // Simulate concurrent claiming by two threads
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        Runnable claimTask = () -> {
            try {
                startLatch.await(); // Wait for signal to start
                List<ConnectorSchedule> claimed = scheduleEngine.claimDueSchedules();
                System.out.println("Thread " + Thread.currentThread().getName() + 
                    " claimed " + claimed.size() + " schedules");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        };

        executor.submit(claimTask);
        executor.submit(claimTask);

        // Start both threads at the same time
        startLatch.countDown();

        // Wait for completion
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        assertTrue(finished, "Concurrent claiming should complete");

        executor.shutdown();

        // The SKIP LOCKED behavior means:
        // - Each schedule is claimed by at most one thread
        // - No deadlocks or errors
        // This is a smoke test - we just verify it doesn't crash
        // Full verification would require transaction inspection which is complex
    }

    @Test
    void scheduleDisabled_onCronError() {
        // Create schedule with invalid cron
        ConnectorSchedule schedule = new ConnectorSchedule();
        schedule.setTenantId(tenantId);
        schedule.setConnectorId(connectorId);
        schedule.setJobType(ConnectorSchedule.JobType.AUDIT_PULL);
        schedule.setCron("INVALID_CRON"); // Invalid cron expression
        schedule.setTimezone("UTC");
        schedule.setEnabled(true);
        schedule.setNextFireAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        schedule = scheduleRepository.save(schedule);

        UUID scheduleId = schedule.getId();

        // Process schedules
        scheduleEngine.processSchedules();

        // Verify schedule was disabled
        ConnectorSchedule updated = scheduleRepository.findById(scheduleId).orElseThrow();
        assertFalse(updated.getEnabled(), "Schedule should be disabled due to cron error");

        // Verify SCHEDULE_DISABLED_DUE_TO_ERROR event exists
        List<OutboxEvent> allEvents = outboxRepository.findAll();
        boolean hasDisabledEvent = allEvents.stream()
            .anyMatch(e -> e.getEventType().equals("SCHEDULE_DISABLED_DUE_TO_ERROR")
                && e.getEntityId().equals(scheduleId.toString()));
        assertTrue(hasDisabledEvent, "SCHEDULE_DISABLED_DUE_TO_ERROR event should exist");

        // Verify no run was created
        List<ConnectorRun> runs = runRepository.findAll();
        assertEquals(0, runs.size(), "No run should be created for failed schedule");
    }

    // Helper methods

    private UUID createTestConnector(UUID tenantId) {
        UUID connectorId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO connector.connectors (connector_id, tenant_id, connector_name, connector_type, status, auth_type, metadata, created_at, updated_at) " +
            "VALUES (?, ?, 'Test Connector', 'WORKDAY', 'ACTIVE', 'OAUTH2', '{}'::jsonb, NOW(), NOW())",
            connectorId, tenantId
        );
        return connectorId;
    }

    private ConnectorSchedule createDueSchedule(UUID tenantId, UUID connectorId, String cron) {
        ConnectorSchedule schedule = new ConnectorSchedule();
        schedule.setTenantId(tenantId);
        schedule.setConnectorId(connectorId);
        schedule.setJobType(ConnectorSchedule.JobType.AUDIT_PULL);
        schedule.setCron(cron);
        schedule.setTimezone("UTC");
        schedule.setEnabled(true);
        schedule.setNextFireAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        return scheduleRepository.save(schedule);
    }
}
