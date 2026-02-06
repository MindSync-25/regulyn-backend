package io.regulyn.connector.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.events.outbox.OutboxEvent;
import com.regulyn.events.outbox.OutboxEventRepository;
import io.regulyn.connector.config.RunWorkerConfig;
import io.regulyn.connector.credentials.CredentialResolutionService;
import io.regulyn.connector.credentials.ResolvedCredentials;
import io.regulyn.connector.cursor.CursorStateService;
import io.regulyn.connector.model.Connector;
import io.regulyn.connector.model.ConnectorCursorState;
import io.regulyn.connector.model.ConnectorRun;
import io.regulyn.connector.repository.ConnectorRepository;
import io.regulyn.connector.repository.ConnectorRunRepository;
import io.regulyn.connector.run.adapter.ConnectorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Worker to execute connector runs with retries and cursor updates.
 */
@Service
public class ConnectorRunWorker {

    private static final Logger logger = LoggerFactory.getLogger(ConnectorRunWorker.class);

    private static final List<Duration> BACKOFF_SCHEDULE = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofMinutes(30),
            Duration.ofMinutes(60)
    );

    private final JdbcTemplate jdbcTemplate;
    private final ConnectorRunRepository runRepository;
    private final ConnectorRepository connectorRepository;
    private final CredentialResolutionService credentialResolutionService;
    private final CursorStateService cursorStateService;
    private final List<ConnectorAdapter> adapters;
    private final OutboxEventRepository outboxEventRepository;
    private final EvidenceServiceClient evidenceServiceClient;
    private final RunWorkerConfig config;
    private final ObjectMapper objectMapper;

    public ConnectorRunWorker(
            JdbcTemplate jdbcTemplate,
            ConnectorRunRepository runRepository,
            ConnectorRepository connectorRepository,
            CredentialResolutionService credentialResolutionService,
            CursorStateService cursorStateService,
            List<ConnectorAdapter> adapters,
            OutboxEventRepository outboxEventRepository,
            EvidenceServiceClient evidenceServiceClient,
            RunWorkerConfig config,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.runRepository = runRepository;
        this.connectorRepository = connectorRepository;
        this.credentialResolutionService = credentialResolutionService;
        this.cursorStateService = cursorStateService;
        this.adapters = adapters;
        this.outboxEventRepository = outboxEventRepository;
        this.evidenceServiceClient = evidenceServiceClient;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * Main worker loop - runs every 10 seconds.
     */
    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void processRuns() {
        try {
            handleStuckRuns();

            List<ConnectorRun> claimedRuns = claimRunnableRuns();
            if (claimedRuns.isEmpty()) {
                return;
            }

            for (ConnectorRun run : claimedRuns) {
                executeRun(run);
            }
        } catch (Exception e) {
            logger.error("Error in run worker", e);
        }
    }

    /**
     * Claim runs using FOR UPDATE SKIP LOCKED.
     */
    @Transactional
    public List<ConnectorRun> claimRunnableRuns() {
        String sql = """
            SELECT * FROM connector.connector_runs
            WHERE status IN ('PENDING','FAILED_RETRYABLE')
              AND (status='PENDING' OR next_retry_at <= NOW())
            ORDER BY created_at ASC
            FOR UPDATE SKIP LOCKED
            LIMIT ?
            """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, config.getBatchSize());
        List<ConnectorRun> runs = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            ConnectorRun run = mapRowToRun(row);
                Instant now = Instant.now();
                java.sql.Timestamp nowTs = java.sql.Timestamp.from(now);
            jdbcTemplate.update(
                    "UPDATE connector.connector_runs SET status='RUNNING', started_at=?, updated_at=? WHERE id=?",
                    nowTs, nowTs, run.getId()
            );
            run.setStatus(ConnectorRun.RunStatus.RUNNING);
            run.setStartedAt(now);

            emitRunEvent("RUN_STARTED", run, null, null);
            runs.add(run);
        }

        return runs;
    }

    /**
     * Mark stuck runs as retryable.
     */
    @Transactional
    public void handleStuckRuns() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(config.getStuckTimeoutMinutes()));
        List<ConnectorRun> stuckRuns = runRepository.findByStatusAndStartedAtBefore(
                ConnectorRun.RunStatus.RUNNING, cutoff);

        for (ConnectorRun run : stuckRuns) {
            logger.warn("Run {} is stuck. Marking retryable.", run.getId());
            markRetryable(run, "RUN_STUCK", "Run exceeded stuck timeout");
            emitRunEvent("RUN_STUCK_RETRYABLE", run, null, null);
        }
    }

    /**
     * Execute a single run.
     */
    @Transactional
    public void executeRun(ConnectorRun run) {
        try {
            Connector connector = connectorRepository.findById(run.getConnectorId())
                    .orElseThrow(() -> new IllegalStateException("Connector not found: " + run.getConnectorId()));

            ResolvedCredentials credentials = credentialResolutionService.resolve(
                    run.getTenantId(), run.getConnectorId(), run.getCorrelationId());

            ConnectorAdapter adapter = selectAdapter(run, connector);

            ExecutionResult result;
            ConnectorCursorState cursorState = null;

            switch (run.getJobType()) {
                case AUDIT_PULL -> {
                    cursorState = cursorStateService.getOrCreateCursorState(
                            run.getTenantId(),
                            run.getConnectorId(),
                            run.getTargetId(),
                            ConnectorCursorState.JobType.AUDIT_PULL
                    );
                    result = adapter.executeAuditPull(run, cursorState, credentials);
                }
                case EXPORT -> {
                    cursorState = cursorStateService.getOrCreateCursorState(
                            run.getTenantId(),
                            run.getConnectorId(),
                            run.getTargetId(),
                            ConnectorCursorState.JobType.EXPORT
                    );
                    result = adapter.executeExport(run, cursorState, credentials);
                }
                case DELETE -> result = adapter.executeDelete(run, credentials);
                default -> throw new IllegalStateException("Unsupported job type: " + run.getJobType());
            }

            // Update cursor only on success
            if (cursorState != null && result.getNewCursorJson() != null) {
                cursorStateService.updateCursorState(cursorState, result.getNewCursorJson());
            }

            // Mark success
            run.setStatus(ConnectorRun.RunStatus.SUCCEEDED);
            run.setFinishedAt(Instant.now());
            runRepository.save(run);

            // Evidence artifact
            String artifactRef = createEvidenceArtifact(run, result, "SUCCEEDED");
            if (artifactRef != null) {
                run.setEvidenceArtifactRef(artifactRef);
                runRepository.save(run);
                emitRunEvent("RUN_EVIDENCE_STORED", run, result, artifactRef);
            }

            emitRunEvent("RUN_SUCCEEDED", run, result, null);

        } catch (Exception e) {
            logger.error("Run execution failed: {}", run.getId(), e);
            handleFailure(run, e);
        }
    }

    private ConnectorAdapter selectAdapter(ConnectorRun run, Connector connector) {
        return adapters.stream()
                .filter(adapter -> adapter.supports(run.getJobType(), connector))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No adapter for job type " + run.getJobType()));
    }

    private void handleFailure(ConnectorRun run, Exception e) {
        int attempts = run.getAttempts() == null ? 0 : run.getAttempts();
        attempts += 1;
        run.setAttempts(attempts);
        run.setLastErrorCode("EXECUTION_FAILED");
        run.setLastErrorMessage(e.getMessage());
        run.setFinishedAt(Instant.now());

        if (attempts < run.getMaxAttempts()) {
            markRetryable(run, "EXECUTION_FAILED", e.getMessage());
            emitRunEvent("RUN_FAILED_RETRYABLE", run, null, null);
        } else {
            run.setStatus(ConnectorRun.RunStatus.FAILED_TERMINAL);
            runRepository.save(run);

            String artifactRef = createEvidenceArtifact(run, null, "FAILED_TERMINAL");
            if (artifactRef != null) {
                run.setEvidenceArtifactRef(artifactRef);
                runRepository.save(run);
                emitRunEvent("RUN_EVIDENCE_STORED", run, null, artifactRef);
            }

            emitRunEvent("RUN_FAILED_TERMINAL", run, null, null);
        }
    }

    private void markRetryable(ConnectorRun run, String errorCode, String errorMessage) {
        int attempts = run.getAttempts() == null ? 0 : run.getAttempts();
        int index = Math.max(0, Math.min(attempts - 1, BACKOFF_SCHEDULE.size() - 1));
        Duration backoff = BACKOFF_SCHEDULE.get(index);

        run.setStatus(ConnectorRun.RunStatus.FAILED_RETRYABLE);
        run.setNextRetryAt(Instant.now().plus(backoff));
        run.setLastErrorCode(errorCode);
        run.setLastErrorMessage(errorMessage);
        run.setFinishedAt(Instant.now());
        runRepository.save(run);
    }

    private String createEvidenceArtifact(ConnectorRun run, ExecutionResult result, String status) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("tenant_id", run.getTenantId().toString());
            payload.put("type", "CONNECTOR_RUN_RESULT");
            payload.put("correlation_id", run.getCorrelationId());
            payload.put("run_id", run.getId().toString());
            payload.put("connector_id", run.getConnectorId().toString());
            payload.put("status", status);
            payload.put("started_at", run.getStartedAt() != null ? run.getStartedAt().toString() : null);
            payload.put("finished_at", run.getFinishedAt() != null ? run.getFinishedAt().toString() : null);
            if (result != null && result.getReceiptMeta() != null) {
                payload.put("receipt_meta", result.getReceiptMeta());
            }

            return evidenceServiceClient.createArtifact(payload);
        } catch (Exception e) {
            logger.warn("Evidence artifact creation failed for run {}: {}", run.getId(), e.getMessage());
            return null;
        }
    }

    private void emitRunEvent(String eventType, ConnectorRun run, ExecutionResult result, String artifactRef) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("runId", run.getId().toString());
            payload.put("tenantId", run.getTenantId().toString());
            payload.put("connectorId", run.getConnectorId().toString());
            if (run.getTargetId() != null) {
                payload.put("targetId", run.getTargetId().toString());
            }
            payload.put("jobType", run.getJobType().name());
            payload.put("status", run.getStatus().name());
            payload.put("correlationId", run.getCorrelationId());
            payload.put("attempts", run.getAttempts());
            if (run.getNextRetryAt() != null) {
                payload.put("nextRetryAt", run.getNextRetryAt().toString());
            }
            if (run.getLastErrorCode() != null) {
                payload.put("lastErrorCode", run.getLastErrorCode());
            }
            if (run.getLastErrorMessage() != null) {
                payload.put("lastErrorMessage", run.getLastErrorMessage());
            }
            if (artifactRef != null) {
                payload.put("artifactRef", artifactRef);
            }
            if (result != null && result.getReceiptMeta() != null) {
                payload.put("receiptMeta", result.getReceiptMeta());
            }

            String payloadJson = objectMapper.writeValueAsString(payload);

            OutboxEvent outbox = new OutboxEvent();
            outbox.setTenantId(run.getTenantId());
            outbox.setEventId(UUID.randomUUID());
            outbox.setEventType(eventType);
            outbox.setSourceService("connector-service");
            outbox.setEntityType("CONNECTOR_RUN");
            outbox.setEntityId(run.getId().toString());
            outbox.setOccurredAt(Instant.now());
            outbox.setCorrelationId(run.getCorrelationId());
            outbox.setPayload(payloadJson);
            outbox.setPayloadHash(Integer.toString(payloadJson.hashCode()));
            outbox.setStatus(com.regulyn.events.outbox.OutboxStatus.PENDING);
            outbox.setNextAttemptAt(Instant.now());

            outboxEventRepository.saveAndFlush(outbox);
        } catch (Exception e) {
            logger.error("Failed to emit run event {} for run {}", eventType, run.getId(), e);
        }
    }

    private ConnectorRun mapRowToRun(Map<String, Object> row) {
        ConnectorRun run = new ConnectorRun();
        run.setId((UUID) row.get("id"));
        run.setTenantId((UUID) row.get("tenant_id"));
        run.setScheduleId((UUID) row.get("schedule_id"));
        run.setConnectorId((UUID) row.get("connector_id"));
        run.setTargetId((UUID) row.get("target_id"));
        run.setJobType(ConnectorRun.JobType.valueOf(row.get("job_type").toString()));
        run.setStatus(ConnectorRun.RunStatus.valueOf(row.get("status").toString()));
        Object attempts = row.get("attempts");
        if (attempts instanceof Number) {
            run.setAttempts(((Number) attempts).intValue());
        }
        Object maxAttempts = row.get("max_attempts");
        if (maxAttempts instanceof Number) {
            run.setMaxAttempts(((Number) maxAttempts).intValue());
        }
        run.setNextRetryAt(row.get("next_retry_at") != null ? ((java.sql.Timestamp) row.get("next_retry_at")).toInstant() : null);
        run.setCorrelationId(row.get("correlation_id") != null ? row.get("correlation_id").toString() : null);
        run.setStartedAt(row.get("started_at") != null ? ((java.sql.Timestamp) row.get("started_at")).toInstant() : null);
        run.setFinishedAt(row.get("finished_at") != null ? ((java.sql.Timestamp) row.get("finished_at")).toInstant() : null);
        run.setLastErrorCode(row.get("last_error_code") != null ? row.get("last_error_code").toString() : null);
        run.setLastErrorMessage(row.get("last_error_message") != null ? row.get("last_error_message").toString() : null);
        run.setCreatedAt(((java.sql.Timestamp) row.get("created_at")).toInstant());
        run.setUpdatedAt(((java.sql.Timestamp) row.get("updated_at")).toInstant());
        return run;
    }
}
