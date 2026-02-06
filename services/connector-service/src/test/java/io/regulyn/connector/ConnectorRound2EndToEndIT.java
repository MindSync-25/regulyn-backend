package io.regulyn.connector;

import com.regulyn.events.outbox.OutboxEvent;
import com.regulyn.events.outbox.OutboxEventRepository;
import io.regulyn.connector.credentials.CredentialResolutionService;
import io.regulyn.connector.credentials.EncryptionService;
import io.regulyn.connector.model.*;
import io.regulyn.connector.repository.*;
import io.regulyn.connector.run.ConnectorRunWorker;
import io.regulyn.connector.run.EvidenceServiceClient;
import io.regulyn.connector.scheduler.ConnectorScheduleEngine;
import io.regulyn.connector.webhook.WebhookSignatureVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ActiveProfiles("fail-adapter")
class ConnectorRound2EndToEndIT extends AbstractIntegrationTestBase {

    @Autowired
    private WebhookSignatureVerifier signatureVerifier;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConnectorScheduleRepository scheduleRepository;

    @Autowired
    private ConnectorScheduleEngine scheduleEngine;

    @Autowired
    private ConnectorRunWorker runWorker;

    @Autowired
    private ConnectorRunRepository runRepository;

    @Autowired
    private ConnectorCursorStateRepository cursorRepository;

    @Autowired
    private CredentialResolutionService credentialResolutionService;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private ConnectorTargetRepository targetRepository;

    @Autowired
    private ConnectorCredentialRepository credentialRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @MockBean
    private EvidenceServiceClient evidenceServiceClient;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM connector.audit_events");
        jdbcTemplate.execute("DELETE FROM connector.outbox_events");
        jdbcTemplate.execute("DELETE FROM connector.webhook_events");
        jdbcTemplate.execute("DELETE FROM connector.connector_runs");
        jdbcTemplate.execute("DELETE FROM connector.connector_schedules");
        jdbcTemplate.execute("DELETE FROM connector.connector_cursor_state");
        jdbcTemplate.execute("DELETE FROM connector.connector_targets");
        jdbcTemplate.execute("DELETE FROM connector.connector_credentials");
        jdbcTemplate.execute("DELETE FROM connector.connectors");

        when(evidenceServiceClient.createArtifact(any())).thenReturn("artifact-xyz");
    }

    @Test
    void schedule_to_run_to_evidence_success() {
        UUID tenantId = UUID.randomUUID();
        Connector connector = createConnector(tenantId, "NOOP");
        ConnectorTarget target = createTarget(tenantId, connector.getConnectorId());
        createCredential(tenantId, connector.getConnectorId(), Map.of("api_key", "dummy"));

        ConnectorSchedule schedule = new ConnectorSchedule();
        schedule.setTenantId(tenantId);
        schedule.setConnectorId(connector.getConnectorId());
        schedule.setTargetId(target.getTargetId());
        schedule.setJobType(ConnectorSchedule.JobType.AUDIT_PULL);
        schedule.setCron("0 0 * * * *");
        schedule.setTimezone("UTC");
        schedule.setEnabled(true);
        schedule.setNextFireAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        schedule = scheduleRepository.save(schedule);

        scheduleEngine.processSchedules();

        List<ConnectorRun> claimed = runWorker.claimRunnableRuns();
        assertEquals(1, claimed.size());
        runWorker.executeRun(claimed.get(0));

        ConnectorRun run = runRepository.findAll().get(0);
        assertEquals(ConnectorRun.RunStatus.SUCCEEDED, run.getStatus());
        assertEquals("artifact-xyz", run.getEvidenceArtifactRef());

        List<ConnectorCursorState> cursors = cursorRepository.findAll();
        assertEquals(1, cursors.size());

        assertOutboxEventExists("SCHEDULE_FIRED");
        assertOutboxEventExists("RUN_CREATED");
        assertOutboxEventExists("RUN_STARTED");
        assertOutboxEventExists("RUN_SUCCEEDED");
        assertOutboxEventExists("RUN_EVIDENCE_STORED");

        assertAuditActionExists("SCHEDULE_FIRED");
        assertAuditActionExists("RUN_CREATED");
        assertAuditActionExists("RUN_STARTED");
        assertAuditActionExists("RUN_SUCCEEDED");
        assertAuditActionExists("RUN_EVIDENCE_STORED");
    }

    @Test
    void webhook_intake_persists_and_emits_events() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Connector connector = createConnector(tenantId, "GITHUB");
        String webhookSecret = "test_webhook_secret";
        createCredential(tenantId, connector.getConnectorId(), Map.of("webhook_secret", webhookSecret));

        String payload = "{\"action\":\"opened\",\"repository\":{\"full_name\":\"octocat/Hello-World\"}}";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        String sig = "sha256=" + computeHmacSha256Hex(payloadBytes, webhookSecret);

        mockMvc.perform(
                post("/api/v1/webhooks/github/" + connector.getConnectorId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadBytes)
                        .header("X-Hub-Signature-256", sig)
                        .header("X-GitHub-Event", "pull_request")
        ).andExpect(status().isOk());

        Integer webhookCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM connector.webhook_events WHERE connector_id=?",
                Integer.class,
                connector.getConnectorId()
        );
        assertNotNull(webhookCount);
        assertEquals(1, webhookCount.intValue());

        assertOutboxEventExists("WEBHOOK_RECEIVED");
        assertOutboxEventExists("WEBHOOK_NORMALIZED");

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM connector.audit_events WHERE entity_type='WEBHOOK_EVENT'",
                Integer.class
        );
        assertNotNull(auditCount);
        assertTrue(auditCount >= 1);
    }

    @Test
    void credential_resolution_audit_outbox() {
        UUID tenantId = UUID.randomUUID();
        Connector connector = createConnector(tenantId, "NOOP");
        createCredential(tenantId, connector.getConnectorId(), Map.of("api_key", "dummy"));

        credentialResolutionService.resolve(tenantId, connector.getConnectorId(), "cred-test");

        assertOutboxEventExists("CREDENTIAL_RESOLVE_STARTED");
        assertOutboxEventExists("CREDENTIAL_RESOLVE_SUCCEEDED");

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM connector.audit_events WHERE entity_type='CONNECTOR_CREDENTIAL'",
                Integer.class
        );
        assertNotNull(auditCount);
        assertTrue(auditCount >= 2);
    }

    @Test
    void retry_flow_terminal() {
        UUID tenantId = UUID.randomUUID();
        Connector connector = createConnector(tenantId, "FAIL");
        createCredential(tenantId, connector.getConnectorId(), Map.of("api_key", "dummy"));

        ConnectorRun run = new ConnectorRun();
        run.setTenantId(tenantId);
        run.setConnectorId(connector.getConnectorId());
        run.setJobType(ConnectorRun.JobType.EXPORT);
        run.setStatus(ConnectorRun.RunStatus.PENDING);
        run.setAttempts(0);
        run.setMaxAttempts(2);
        run.setCorrelationId(UUID.randomUUID().toString());
        runRepository.save(run);

        for (int i = 0; i < 2; i++) {
            List<ConnectorRun> claimed = runWorker.claimRunnableRuns();
            if (claimed.isEmpty()) {
                run = runRepository.findById(run.getId()).orElseThrow();
                run.setNextRetryAt(Instant.now().minusSeconds(1));
                run.setStatus(ConnectorRun.RunStatus.FAILED_RETRYABLE);
                runRepository.save(run);
                claimed = runWorker.claimRunnableRuns();
            }
            if (!claimed.isEmpty()) {
                runWorker.executeRun(claimed.get(0));
            }
        }

        ConnectorRun updated = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(ConnectorRun.RunStatus.FAILED_TERMINAL, updated.getStatus());
        assertEquals("artifact-xyz", updated.getEvidenceArtifactRef());

        assertOutboxEventExists("RUN_FAILED_TERMINAL");
        assertAuditActionExists("RUN_FAILED_TERMINAL");
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

    private ConnectorTarget createTarget(UUID tenantId, UUID connectorId) {
        ConnectorTarget target = new ConnectorTarget();
        target.setTenantId(tenantId);
        target.setConnectorId(connectorId);
        target.setTargetKey("target-1");
        target.setTargetType("USER");
        target.setSubjectType("PERSON");
        target.setSupportedActions(List.of("AUDIT_PULL", "EXPORT", "DELETE"));
        target.setRequiresApproval(false);
        return targetRepository.save(target);
    }

    private void createCredential(UUID tenantId, UUID connectorId, Map<String, Object> creds) {
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(creds);
            EncryptionService.EncryptedData encrypted = encryptionService.encrypt(json.getBytes(StandardCharsets.UTF_8));

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

    private void assertOutboxEventExists(String eventType) {
        List<OutboxEvent> all = outboxRepository.findAll();
        assertTrue(all.stream().anyMatch(e -> eventType.equals(e.getEventType())));
    }

    private void assertAuditActionExists(String action) {
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM connector.audit_events WHERE action=?",
                Integer.class,
                action
        );
        assertNotNull(auditCount);
        assertTrue(auditCount >= 1);
    }

    private String computeHmacSha256Hex(byte[] payload, String secret) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] hmacBytes = mac.doFinal(payload);

        StringBuilder hexString = new StringBuilder();
        for (byte b : hmacBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

}
