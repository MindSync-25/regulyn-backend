package io.regulyn.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.outbox.OutboxEvent;
import com.regulyn.events.outbox.OutboxEventRepository;
import io.regulyn.connector.model.ConnectorRun;
import io.regulyn.connector.model.ConnectorSchedule;
import io.regulyn.connector.repository.ConnectorRunRepository;
import io.regulyn.connector.repository.ConnectorScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduled sync engine that claims due schedules and creates connector runs.
 * Uses SELECT FOR UPDATE SKIP LOCKED for distributed scheduling.
 */
@Service
public class SchedulerService {
    private static final Logger logger = LoggerFactory.getLogger(SchedulerService.class);
    
    private final ConnectorScheduleRepository scheduleRepository;
    private final ConnectorRunRepository runRepository;
    private final OutboxEventRepository outboxRepository;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    
    public SchedulerService(
        ConnectorScheduleRepository scheduleRepository,
        ConnectorRunRepository runRepository,
        OutboxEventRepository outboxRepository,
        AuditWriter auditWriter,
        ObjectMapper objectMapper
    ) {
        this.scheduleRepository = scheduleRepository;
        this.runRepository = runRepository;
        this.outboxRepository = outboxRepository;
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Run every 30 seconds to claim and execute due schedules.
     */
    @Scheduled(fixedDelayString = "${connector.scheduler.poll-interval-ms:30000}")
    @Transactional
    public void processDueSchedules() {
        Instant now = Instant.now();
        
        try {
            List<ConnectorSchedule> dueSchedules = scheduleRepository.findDueSchedulesForUpdate(now);
            
            if (dueSchedules.isEmpty()) {
                logger.trace("No due schedules found at {}", now);
                return;
            }
            
            logger.info("Found {} due schedules to process", dueSchedules.size());
            
            for (ConnectorSchedule schedule : dueSchedules) {
                try {
                    processSchedule(schedule, now);
                } catch (Exception e) {
                    logger.error("Failed to process schedule: {}", schedule.getScheduleId(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing due schedules", e);
        }
    }
    
    private void processSchedule(ConnectorSchedule schedule, Instant fireTime) {
        // Check for existing run with same fire time (idempotency)
        boolean runExists = runRepository
            .findByScheduleIdAndScheduledFireTime(schedule.getScheduleId(), fireTime)
            .isPresent();
        
        if (runExists) {
            logger.debug("Run already exists for schedule {} at fire time {}, skipping", 
                schedule.getScheduleId(), fireTime);
            updateNextRunTime(schedule);
            return;
        }
        
        // Create connector run
        ConnectorRun run = new ConnectorRun();
        run.setTenantId(schedule.getTenantId());
        run.setConnectorId(schedule.getConnectorId());
        run.setTargetId(schedule.getTargetId());
        run.setRunType("SCHEDULED");
        run.setScheduleId(schedule.getScheduleId());
        run.setJobType(schedule.getJobType());
        run.setStatus("PENDING");
        run.setScheduledFireTime(fireTime);
        run.setIdempotencyKey(buildIdempotencyKey(schedule, fireTime));
        
        run = runRepository.save(run);
        
        logger.info("Created scheduled connector run: {} for schedule: {}", 
            run.getRunId(), schedule.getScheduleId());
        
        // Emit audit event
        auditWriter.auditAction(
            "SCHEDULE_FIRED",
            "CONNECTOR_SCHEDULE",
            schedule.getScheduleId().toString(),
            "N/A",
            null,
            null
        );
        
        // Emit outbox event
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setTenantId(schedule.getTenantId());
        outboxEvent.setEventId(UUID.randomUUID());
        outboxEvent.setEventType("CONNECTOR_RUN_CREATED");
        outboxEvent.setSourceService("connector-service");
        outboxEvent.setEntityType("CONNECTOR_RUN");
        outboxEvent.setEntityId(run.getRunId().toString());
        outboxEvent.setOccurredAt(Instant.now());
        outboxEvent.setCorrelationId(run.getRunId().toString());
        
        try {
            String payloadJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                "run_id", run.getRunId().toString(),
                "run_type", "SCHEDULED",
                "schedule_id", schedule.getScheduleId().toString(),
                "connector_id", schedule.getConnectorId().toString(),
                "job_type", schedule.getJobType(),
                "status", "PENDING"
            ));
            outboxEvent.setPayload(payloadJson);
            outboxEvent.setPayloadHash(Integer.toString(payloadJson.hashCode()));
        } catch (Exception e) {
            logger.error("Failed to serialize outbox payload", e);
        }
        
        outboxRepository.save(outboxEvent);
        
        // Update schedule's next run time
        updateNextRunTime(schedule);
    }
    
    private void updateNextRunTime(ConnectorSchedule schedule) {
        Instant now = Instant.now();
        schedule.setLastRunAt(now);
        
        // Calculate next run time based on interval or cron
        if (schedule.getIntervalSeconds() != null) {
            schedule.setNextRunAt(now.plusSeconds(schedule.getIntervalSeconds()));
        } else if (schedule.getCronExpr() != null) {
            // For cron, we'd need a cron parser (e.g., spring-context-support CronExpression)
            // For now, default to 1 hour if cron is set
            schedule.setNextRunAt(now.plusSeconds(3600));
            logger.warn("Cron expression parsing not implemented, using 1-hour default for schedule: {}", 
                schedule.getScheduleId());
        }
        
        scheduleRepository.save(schedule);
    }
    
    private String buildIdempotencyKey(ConnectorSchedule schedule, Instant fireTime) {
        return String.format("schedule:%s:fire:%d", 
            schedule.getScheduleId(), 
            fireTime.getEpochSecond()
        );
    }
    
    /**
     * Create a new schedule.
     */
    @Transactional
    public ConnectorSchedule createSchedule(
        UUID tenantId,
        UUID connectorId,
        UUID targetId,
        String jobType,
        String cronExpr,
        Integer intervalSeconds
    ) {
        ConnectorSchedule schedule = new ConnectorSchedule();
        schedule.setTenantId(tenantId);
        schedule.setConnectorId(connectorId);
        schedule.setTargetId(targetId);
        schedule.setJobType(jobType);
        schedule.setCronExpr(cronExpr);
        schedule.setIntervalSeconds(intervalSeconds);
        schedule.setEnabled(true);
        
        // Set initial next run time
        Instant now = Instant.now();
        if (intervalSeconds != null) {
            schedule.setNextRunAt(now.plusSeconds(intervalSeconds));
        } else {
            schedule.setNextRunAt(now.plusSeconds(3600)); // Default 1 hour
        }
        
        schedule = scheduleRepository.save(schedule);
        
        logger.info("Created connector schedule: {} for connector: {}", 
            schedule.getScheduleId(), connectorId);
        
        return schedule;
    }
}
