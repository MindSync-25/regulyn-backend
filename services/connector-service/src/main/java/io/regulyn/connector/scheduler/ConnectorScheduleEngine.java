package io.regulyn.connector.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.events.outbox.OutboxEvent;
import com.regulyn.events.outbox.OutboxEventRepository;
import io.regulyn.connector.config.SchedulerConfig;
import io.regulyn.connector.model.ConnectorRun;
import io.regulyn.connector.model.ConnectorSchedule;
import io.regulyn.connector.repository.ConnectorRunRepository;
import io.regulyn.connector.repository.ConnectorScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

/**
 * Distributed scheduler engine that claims due schedules and creates connector runs.
 * Uses FOR UPDATE SKIP LOCKED for distributed safety.
 */
@Component
public class ConnectorScheduleEngine {

    private static final Logger logger = LoggerFactory.getLogger(ConnectorScheduleEngine.class);

    private final JdbcTemplate jdbcTemplate;
    private final ConnectorScheduleRepository scheduleRepository;
    private final ConnectorRunRepository runRepository;
    private final OutboxEventRepository outboxRepository;
    private final SchedulerConfig config;
    private final ObjectMapper objectMapper;

    public ConnectorScheduleEngine(
            JdbcTemplate jdbcTemplate,
            ConnectorScheduleRepository scheduleRepository,
            ConnectorRunRepository runRepository,
            OutboxEventRepository outboxRepository,
            SchedulerConfig config,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.scheduleRepository = scheduleRepository;
        this.runRepository = runRepository;
        this.outboxRepository = outboxRepository;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * Main scheduler loop - runs every 30 seconds.
     * Claims due schedules and creates runs.
     */
    @Scheduled(fixedDelay = 30000)
    public void processSchedules() {
        try {
            logger.debug("Scheduler engine starting batch processing");
            List<ConnectorSchedule> claimedSchedules = claimDueSchedules();
            
            if (claimedSchedules.isEmpty()) {
                logger.debug("No due schedules to process");
                return;
            }

            logger.info("Claimed {} schedules for processing", claimedSchedules.size());
            
            for (ConnectorSchedule schedule : claimedSchedules) {
                processSchedule(schedule);
            }
            
        } catch (Exception e) {
            logger.error("Error in scheduler engine batch processing", e);
        }
    }

    /**
     * Claims due schedules using FOR UPDATE SKIP LOCKED for distributed safety.
     * Returns schedules that are ready to fire.
     */
    public List<ConnectorSchedule> claimDueSchedules() {
        String sql = """
            SELECT * FROM connector.connector_schedules
            WHERE enabled = true
              AND next_fire_at <= NOW()
            ORDER BY next_fire_at ASC
            FOR UPDATE SKIP LOCKED
            LIMIT ?
            """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, config.getBatchSize());
        List<ConnectorSchedule> schedules = new ArrayList<>();
        
        for (Map<String, Object> row : rows) {
            ConnectorSchedule schedule = mapRowToSchedule(row);
            schedules.add(schedule);
        }
        
        return schedules;
    }

    /**
     * Process a single claimed schedule:
     * 1. Compute next fire time
     * 2. Create run idempotently
     * 3. Update schedule
     * 4. Emit events
     */
    @Transactional
    public void processSchedule(ConnectorSchedule schedule) {
        try {
            Instant now = Instant.now();
            Instant fireTime = schedule.getLastFireAt() != null ? schedule.getLastFireAt() : now;
            
            // Compute next fire time
            Instant nextFireAt;
            try {
                nextFireAt = computeNextFireTime(schedule.getCron(), schedule.getTimezone(), fireTime);
            } catch (Exception e) {
                logger.error("Failed to compute next fire time for schedule {}: {}", 
                    schedule.getId(), e.getMessage());
                disableSchedule(schedule, "CRON_PARSE_ERROR", e.getMessage());
                return;
            }

            // Check for duplicate run (idempotency)
            if (isDuplicateRun(schedule, fireTime)) {
                logger.warn("Skipping duplicate run for schedule {} at fire time {}", 
                    schedule.getId(), fireTime);
                // Still update schedule to move it forward
                updateSchedule(schedule, fireTime, nextFireAt);
                return;
            }

            // Create connector run
            ConnectorRun run = createRun(schedule, fireTime);
            
            // Update schedule
            updateSchedule(schedule, fireTime, nextFireAt);
            
            // Emit events
            emitScheduleFiredEvent(schedule, run);
            emitRunCreatedEvent(run);
            
            logger.info("Successfully processed schedule {}: created run {}, next fire at {}", 
                schedule.getId(), run.getId(), nextFireAt);
                
        } catch (Exception e) {
            logger.error("Error processing schedule {}", schedule.getId(), e);
            // Don't disable schedule on processing errors - will retry next cycle
        }
    }

    /**
     * Compute next fire time using cron expression and timezone.
     */
    private Instant computeNextFireTime(String cronExpr, String timezone, Instant fromTime) {
        CronExpression cron = CronExpression.parse(cronExpr);
        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime fromZdt = fromTime.atZone(zoneId);
        LocalDateTime next = cron.next(fromZdt.toLocalDateTime());
        
        if (next == null) {
            throw new IllegalStateException("Cron expression has no future executions: " + cronExpr);
        }
        
        return next.atZone(zoneId).toInstant();
    }

    /**
     * Check if a run already exists for this schedule fire time (idempotency).
     * Checks for runs within +/- 1 minute of the fire time.
     */
    private boolean isDuplicateRun(ConnectorSchedule schedule, Instant fireTime) {
        Instant windowStart = fireTime.minusSeconds(60);
        Instant windowEnd = fireTime.plusSeconds(60);
        
        long count = runRepository.countByScheduleIdAndCreatedAtBetween(
            schedule.getId(), windowStart, windowEnd
        );
        
        return count > 0;
    }

    /**
     * Create a connector run for the schedule.
     */
    private ConnectorRun createRun(ConnectorSchedule schedule, Instant fireTime) {
        ConnectorRun run = new ConnectorRun();
        run.setTenantId(schedule.getTenantId());
        run.setScheduleId(schedule.getId());
        run.setConnectorId(schedule.getConnectorId());
        run.setTargetId(schedule.getTargetId());
        run.setJobType(ConnectorRun.JobType.valueOf(schedule.getJobType().name()));
        run.setStatus(ConnectorRun.RunStatus.PENDING);
        run.setAttempts(0);
        run.setMaxAttempts(5);
        run.setCorrelationId(generateCorrelationId(schedule));
        
        return runRepository.save(run);
    }

    /**
     * Update schedule with new fire times.
     */
    private void updateSchedule(ConnectorSchedule schedule, Instant lastFireAt, Instant nextFireAt) {
        schedule.setLastFireAt(lastFireAt);
        schedule.setNextFireAt(nextFireAt);
        scheduleRepository.save(schedule);
    }

    /**
     * Disable a schedule due to an error.
     */
    private void disableSchedule(ConnectorSchedule schedule, String errorCode, String errorMessage) {
        schedule.setEnabled(false);
        scheduleRepository.save(schedule);
        
        emitScheduleDisabledEvent(schedule, errorCode, errorMessage);
    }

    /**
     * Generate correlation ID for a run.
     * Format: schedule-{scheduleId}-{timestamp}
     */
    private String generateCorrelationId(ConnectorSchedule schedule) {
        String prefix = "schedule-" + schedule.getId().toString().substring(0, 8);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String correlationId = prefix + "-" + timestamp;
        
        // Ensure max 64 chars
        if (correlationId.length() > 64) {
            correlationId = correlationId.substring(0, 64);
        }
        
        return correlationId;
    }

    /**
     * Emit SCHEDULE_FIRED event.
     */
    private void emitScheduleFiredEvent(ConnectorSchedule schedule, ConnectorRun run) {
        OutboxEvent event = new OutboxEvent();
        event.setTenantId(schedule.getTenantId());
        event.setEventId(UUID.randomUUID());
        event.setEventType("SCHEDULE_FIRED");
        event.setSourceService("connector-service");
        event.setEntityType("CONNECTOR_SCHEDULE");
        event.setEntityId(schedule.getId().toString());
        event.setOccurredAt(Instant.now());
        event.setCorrelationId(run.getCorrelationId());
        event.setStatus(com.regulyn.events.outbox.OutboxStatus.PENDING);
        event.setNextAttemptAt(Instant.now());
        
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("scheduleId", schedule.getId().toString());
            payload.put("connectorId", schedule.getConnectorId().toString());
            if (schedule.getTargetId() != null) {
                payload.put("targetId", schedule.getTargetId().toString());
            }
            payload.put("jobType", schedule.getJobType().name());
            payload.put("runId", run.getId().toString());
            payload.put("correlationId", run.getCorrelationId());
            
            String payloadJson = objectMapper.writeValueAsString(payload);
            event.setPayload(payloadJson);
            event.setPayloadHash(Integer.toString(payloadJson.hashCode()));
        } catch (Exception e) {
            logger.error("Failed to serialize SCHEDULE_FIRED payload", e);
        }
        
        outboxRepository.saveAndFlush(event);
    }

    /**
     * Emit RUN_CREATED event.
     */
    private void emitRunCreatedEvent(ConnectorRun run) {
        OutboxEvent event = new OutboxEvent();
        event.setTenantId(run.getTenantId());
        event.setEventId(UUID.randomUUID());
        event.setEventType("RUN_CREATED");
        event.setSourceService("connector-service");
        event.setEntityType("CONNECTOR_RUN");
        event.setEntityId(run.getId().toString());
        event.setOccurredAt(Instant.now());
        event.setCorrelationId(run.getCorrelationId());
        event.setStatus(com.regulyn.events.outbox.OutboxStatus.PENDING);
        event.setNextAttemptAt(Instant.now());
        
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("runId", run.getId().toString());
            payload.put("scheduleId", run.getScheduleId() != null ? run.getScheduleId().toString() : null);
            payload.put("connectorId", run.getConnectorId().toString());
            if (run.getTargetId() != null) {
                payload.put("targetId", run.getTargetId().toString());
            }
            payload.put("jobType", run.getJobType().name());
            payload.put("status", run.getStatus().name());
            payload.put("correlationId", run.getCorrelationId());
            
            String payloadJson = objectMapper.writeValueAsString(payload);
            event.setPayload(payloadJson);
            event.setPayloadHash(Integer.toString(payloadJson.hashCode()));
        } catch (Exception e) {
            logger.error("Failed to serialize RUN_CREATED payload", e);
        }
        
        outboxRepository.saveAndFlush(event);
    }

    /**
     * Emit SCHEDULE_DISABLED_DUE_TO_ERROR event.
     */
    private void emitScheduleDisabledEvent(ConnectorSchedule schedule, String errorCode, String errorMessage) {
        OutboxEvent event = new OutboxEvent();
        event.setTenantId(schedule.getTenantId());
        event.setEventId(UUID.randomUUID());
        event.setEventType("SCHEDULE_DISABLED_DUE_TO_ERROR");
        event.setSourceService("connector-service");
        event.setEntityType("CONNECTOR_SCHEDULE");
        event.setEntityId(schedule.getId().toString());
        event.setOccurredAt(Instant.now());
        event.setCorrelationId(UUID.randomUUID().toString());
        event.setStatus(com.regulyn.events.outbox.OutboxStatus.PENDING);
        event.setNextAttemptAt(Instant.now());
        
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("scheduleId", schedule.getId().toString());
            payload.put("connectorId", schedule.getConnectorId().toString());
            if (schedule.getTargetId() != null) {
                payload.put("targetId", schedule.getTargetId().toString());
            }
            payload.put("jobType", schedule.getJobType().name());
            payload.put("errorCode", errorCode);
            payload.put("errorMessage", errorMessage);
            
            String payloadJson = objectMapper.writeValueAsString(payload);
            event.setPayload(payloadJson);
            event.setPayloadHash(Integer.toString(payloadJson.hashCode()));
        } catch (Exception e) {
            logger.error("Failed to serialize SCHEDULE_DISABLED_DUE_TO_ERROR payload", e);
        }
        
        outboxRepository.saveAndFlush(event);
    }

    /**
     * Map JDBC result row to ConnectorSchedule entity.
     */
    private ConnectorSchedule mapRowToSchedule(Map<String, Object> row) {
        ConnectorSchedule schedule = new ConnectorSchedule();
        schedule.setId((UUID) row.get("id"));
        schedule.setTenantId((UUID) row.get("tenant_id"));
        schedule.setConnectorId((UUID) row.get("connector_id"));
        schedule.setTargetId((UUID) row.get("target_id"));
        schedule.setJobType(ConnectorSchedule.JobType.valueOf((String) row.get("job_type")));
        schedule.setCron((String) row.get("cron"));
        schedule.setTimezone((String) row.get("timezone"));
        schedule.setEnabled((Boolean) row.get("enabled"));
        
        Object nextFireAt = row.get("next_fire_at");
        if (nextFireAt instanceof java.sql.Timestamp) {
            schedule.setNextFireAt(((java.sql.Timestamp) nextFireAt).toInstant());
        }
        
        Object lastFireAt = row.get("last_fire_at");
        if (lastFireAt instanceof java.sql.Timestamp) {
            schedule.setLastFireAt(((java.sql.Timestamp) lastFireAt).toInstant());
        }
        
        Object createdAt = row.get("created_at");
        if (createdAt instanceof java.sql.Timestamp) {
            schedule.setCreatedAt(((java.sql.Timestamp) createdAt).toInstant());
        }
        
        Object updatedAt = row.get("updated_at");
        if (updatedAt instanceof java.sql.Timestamp) {
            schedule.setUpdatedAt(((java.sql.Timestamp) updatedAt).toInstant());
        }
        
        return schedule;
    }
}
