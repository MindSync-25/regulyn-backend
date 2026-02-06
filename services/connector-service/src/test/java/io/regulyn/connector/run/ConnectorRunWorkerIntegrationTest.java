package io.regulyn.connector.run;

import com.regulyn.events.outbox.OutboxEvent;
import com.regulyn.events.outbox.OutboxEventRepository;
import io.regulyn.connector.AbstractIntegrationTestBase;
import io.regulyn.connector.credentials.EncryptionService;
import io.regulyn.connector.model.Connector;
import io.regulyn.connector.model.ConnectorCredential;
import io.regulyn.connector.model.ConnectorCursorState;
import io.regulyn.connector.model.ConnectorRun;
import io.regulyn.connector.repository.ConnectorCredentialRepository;
import io.regulyn.connector.repository.ConnectorCursorStateRepository;
import io.regulyn.connector.repository.ConnectorRepository;
import io.regulyn.connector.repository.ConnectorRunRepository;
import io.regulyn.connector.run.adapter.ConnectorAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for ConnectorRunWorker.
 */
class ConnectorRunWorkerIntegrationTest extends AbstractIntegrationTestBase {

    @Autowired
    private ConnectorRunWorker runWorker;

    @Autowired
    private ConnectorRunRepository runRepository;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private ConnectorCredentialRepository credentialRepository;

    @Autowired
    private ConnectorCursorStateRepository cursorRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RunAdminController runAdminController;

    @MockBean
    private EvidenceServiceClient evidenceServiceClient;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM connector.outbox_events");
        jdbcTemplate.execute("DELETE FROM connector.connector_runs");
        jdbcTemplate.execute("DELETE FROM connector.connector_cursor_state");
        jdbcTemplate.execute("DELETE FROM connector.connector_credentials");
        jdbcTemplate.execute("DELETE FROM connector.connectors");

        when(evidenceServiceClient.createArtifact(any())).thenReturn("artifact-123");
    }

    @Test
    void pendingRun_isClaimedAndExecuted_successPath_marksSucceeded_emitsAuditOutbox() {
        UUID tenantId = UUID.randomUUID();
        Connector connector = createConnector(tenantId, "NOOP");
        createCredential(connector.getTenantId(), connector.getConnectorId());

        ConnectorRun run = createRun(connector, ConnectorRun.JobType.AUDIT_PULL, ConnectorRun.RunStatus.PENDING, 0, 3);

        List<ConnectorRun> claimed = runWorker.claimRunnableRuns();
        assertEquals(1, claimed.size());
        runWorker.executeRun(claimed.get(0));

        ConnectorRun updated = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(ConnectorRun.RunStatus.SUCCEEDED, updated.getStatus());
        assertNotNull(updated.getFinishedAt());
        assertEquals("artifact-123", updated.getEvidenceArtifactRef());

        List<OutboxEvent> events = outboxRepository.findByEntityTypeAndEntityIdOrderByOccurredAtAsc(
                "CONNECTOR_RUN", run.getId().toString());
        assertTrue(events.stream().anyMatch(e -> "RUN_STARTED".equals(e.getEventType())));
        assertTrue(events.stream().anyMatch(e -> "RUN_SUCCEEDED".equals(e.getEventType())));
        assertTrue(events.stream().anyMatch(e -> "RUN_EVIDENCE_STORED".equals(e.getEventType())));

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM connector.audit_events WHERE entity_type='CONNECTOR_RUN' AND entity_id=?",
            Integer.class,
            run.getId().toString()
        );
        assertNotNull(auditCount);
        assertTrue(auditCount >= 1);
    }

    @Test
    void failure_marksRetryable_andSchedulesBackoff() {
        UUID tenantId = UUID.randomUUID();
        Connector connector = createConnector(tenantId, "FAIL");
        createCredential(connector.getTenantId(), connector.getConnectorId());

        ConnectorRun run = createRun(connector, ConnectorRun.JobType.EXPORT, ConnectorRun.RunStatus.PENDING, 0, 5);

        List<ConnectorRun> claimed = runWorker.claimRunnableRuns();
        assertEquals(1, claimed.size());
        runWorker.executeRun(claimed.get(0));

        ConnectorRun updated = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(ConnectorRun.RunStatus.FAILED_RETRYABLE, updated.getStatus());
        assertNotNull(updated.getNextRetryAt());
        assertEquals(1, updated.getAttempts());

        List<OutboxEvent> events = outboxRepository.findByEntityTypeAndEntityIdOrderByOccurredAtAsc(
                "CONNECTOR_RUN", run.getId().toString());
        assertTrue(events.stream().anyMatch(e -> "RUN_FAILED_RETRYABLE".equals(e.getEventType())));

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM connector.audit_events WHERE entity_type='CONNECTOR_RUN' AND entity_id=? AND action='RUN_FAILED_RETRYABLE'",
            Integer.class,
            run.getId().toString()
        );
        assertNotNull(auditCount);
        assertTrue(auditCount >= 1);
    }

    @Test
    void maxAttempts_marksTerminal() {
        UUID tenantId = UUID.randomUUID();
        Connector connector = createConnector(tenantId, "FAIL");
        createCredential(connector.getTenantId(), connector.getConnectorId());

        ConnectorRun run = createRun(connector, ConnectorRun.JobType.EXPORT, ConnectorRun.RunStatus.PENDING, 0, 1);

        List<ConnectorRun> claimed = runWorker.claimRunnableRuns();
        assertEquals(1, claimed.size());
        runWorker.executeRun(claimed.get(0));

        ConnectorRun updated = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(ConnectorRun.RunStatus.FAILED_TERMINAL, updated.getStatus());
        assertEquals("artifact-123", updated.getEvidenceArtifactRef());

        List<OutboxEvent> events = outboxRepository.findByEntityTypeAndEntityIdOrderByOccurredAtAsc(
                "CONNECTOR_RUN", run.getId().toString());
        assertTrue(events.stream().anyMatch(e -> "RUN_FAILED_TERMINAL".equals(e.getEventType())));
        assertTrue(events.stream().anyMatch(e -> "RUN_EVIDENCE_STORED".equals(e.getEventType())));

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM connector.audit_events WHERE entity_type='CONNECTOR_RUN' AND entity_id=? AND action='RUN_FAILED_TERMINAL'",
            Integer.class,
            run.getId().toString()
        );
        assertNotNull(auditCount);
        assertTrue(auditCount >= 1);
    }

    @Test
    void cursorState_createdAndUpdatedOnAuditPull() {
        UUID tenantId = UUID.randomUUID();
        Connector connector = createConnector(tenantId, "NOOP");
        createCredential(connector.getTenantId(), connector.getConnectorId());

        ConnectorRun run = createRun(connector, ConnectorRun.JobType.AUDIT_PULL, ConnectorRun.RunStatus.PENDING, 0, 3);

        List<ConnectorRun> claimed = runWorker.claimRunnableRuns();
        assertEquals(1, claimed.size());
        runWorker.executeRun(claimed.get(0));

        List<ConnectorCursorState> cursors = cursorRepository.findAll();
        assertEquals(1, cursors.size());
        Map<String, Object> cursorJson = cursors.get(0).getCursorJson();
        assertEquals(run.getId().toString(), cursorJson.get("lastRunId"));
        assertNotNull(cursorJson.get("ts"));
    }

    @Test
    void stuckRunning_runIsMarkedRetryableAfterTimeout() {
        UUID tenantId = UUID.randomUUID();
        Connector connector = createConnector(tenantId, "NOOP");
        createCredential(connector.getTenantId(), connector.getConnectorId());

        ConnectorRun run = createRun(connector, ConnectorRun.JobType.AUDIT_PULL, ConnectorRun.RunStatus.RUNNING, 0, 3);
        run.setStartedAt(Instant.now().minus(20, ChronoUnit.MINUTES));
        runRepository.save(run);

        runWorker.handleStuckRuns();

        ConnectorRun updated = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(ConnectorRun.RunStatus.FAILED_RETRYABLE, updated.getStatus());
        assertNotNull(updated.getNextRetryAt());

        List<OutboxEvent> events = outboxRepository.findByEntityTypeAndEntityIdOrderByOccurredAtAsc(
                "CONNECTOR_RUN", run.getId().toString());
        assertTrue(events.stream().anyMatch(e -> "RUN_STUCK_RETRYABLE".equals(e.getEventType())));

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM connector.audit_events WHERE entity_type='CONNECTOR_RUN' AND entity_id=? AND action='RUN_STUCK_RETRYABLE'",
            Integer.class,
            run.getId().toString()
        );
        assertNotNull(auditCount);
        assertTrue(auditCount >= 1);
    }

    @Test
    void retryEndpoint_forcesRetry() {
        UUID tenantId = UUID.randomUUID();
        Connector connector = createConnector(tenantId, "NOOP");
        createCredential(connector.getTenantId(), connector.getConnectorId());

        ConnectorRun run = createRun(connector, ConnectorRun.JobType.AUDIT_PULL, ConnectorRun.RunStatus.FAILED_TERMINAL, 1, 1);
        runRepository.save(run);

        ConnectorRun updated = runAdminController.retryRun(run.getId()).getBody();
        assertNotNull(updated);
        assertEquals(ConnectorRun.RunStatus.FAILED_RETRYABLE, updated.getStatus());
        assertNotNull(updated.getNextRetryAt());
    }

    @Test
    void evidenceArtifact_createdAndStored_onTerminalOrSuccess() {
        UUID tenantId = UUID.randomUUID();
        Connector connector = createConnector(tenantId, "NOOP");
        createCredential(connector.getTenantId(), connector.getConnectorId());

        ConnectorRun run = createRun(connector, ConnectorRun.JobType.AUDIT_PULL, ConnectorRun.RunStatus.PENDING, 0, 3);
        List<ConnectorRun> claimed = runWorker.claimRunnableRuns();
        assertEquals(1, claimed.size());
        runWorker.executeRun(claimed.get(0));

        ConnectorRun updated = runRepository.findById(run.getId()).orElseThrow();
        assertEquals("artifact-123", updated.getEvidenceArtifactRef());
    }

    private Connector createConnector(UUID tenantId, String connectorType) {
        Connector connector = new Connector();
        connector.setTenantId(tenantId);
        connector.setConnectorName("test-connector");
        connector.setConnectorType(connectorType);
        connector.setStatus("ACTIVE");
        connector.setAuthType("API_KEY");
        return connectorRepository.save(connector);
    }

    private void createCredential(UUID tenantId, UUID connectorId) {
        Map<String, Object> creds = new HashMap<>();
        creds.put("api_key", "dummy");

        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(creds);
            EncryptionService.EncryptedData encrypted = encryptionService.encrypt(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] ivBytes = java.util.Base64.getDecoder().decode(encrypted.getIv());
            byte[] encryptedBytes = java.util.Base64.getDecoder().decode(encrypted.getEncPayload());

            ConnectorCredential credential = new ConnectorCredential();
            credential.setTenantId(tenantId);
            credential.setConnectorId(connectorId);
            credential.setProvider(ConnectorCredential.CredentialProvider.LOCAL_DB_ENCRYPTED);
            credential.setEncIv(ivBytes);
            credential.setEncPayload(encryptedBytes);
            credentialRepository.saveAndFlush(credential);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ConnectorRun createRun(Connector connector, ConnectorRun.JobType jobType, ConnectorRun.RunStatus status, int attempts, int maxAttempts) {
        ConnectorRun run = new ConnectorRun();
        run.setTenantId(connector.getTenantId());
        run.setConnectorId(connector.getConnectorId());
        run.setJobType(jobType);
        run.setStatus(status);
        run.setAttempts(attempts);
        run.setMaxAttempts(maxAttempts);
        run.setCorrelationId(UUID.randomUUID().toString());
        run.setCreatedAt(Instant.now());
        run.setUpdatedAt(Instant.now());
        return runRepository.saveAndFlush(run);
    }

    @TestConfiguration
    static class FailingAdapterConfig {
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        ConnectorAdapter failingAdapter() {
            return new ConnectorAdapter() {
                @Override
                public boolean supports(ConnectorRun.JobType jobType, Connector connector) {
                    return "FAIL".equalsIgnoreCase(connector.getConnectorType());
                }

                @Override
                public ExecutionResult executeAuditPull(ConnectorRun run, ConnectorCursorState cursorState, io.regulyn.connector.credentials.ResolvedCredentials credentials) {
                    throw new RuntimeException("fail");
                }

                @Override
                public ExecutionResult executeExport(ConnectorRun run, ConnectorCursorState cursorState, io.regulyn.connector.credentials.ResolvedCredentials credentials) {
                    throw new RuntimeException("fail");
                }

                @Override
                public ExecutionResult executeDelete(ConnectorRun run, io.regulyn.connector.credentials.ResolvedCredentials credentials) {
                    throw new RuntimeException("fail");
                }
            };
        }
    }
}
