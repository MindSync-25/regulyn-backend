package io.regulyn.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.events.outbox.OutboxEventRepository;
import io.regulyn.connector.credentials.CredentialService;
import io.regulyn.connector.model.*;
import io.regulyn.connector.repository.*;
import io.regulyn.connector.service.SchedulerService;
import io.regulyn.connector.service.WebhookService;
import io.regulyn.connector.webhook.WebhookSignatureVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for connector scheduler, webhooks, and credentials.
 */
@SpringBootTest
@Testcontainers
class ConnectorHardeningIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("regulyn")
        .withUsername("regulyn_user")
        .withPassword("test-password");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("connector.scheduler.enabled", () -> "false"); // Disable auto-scheduling for tests
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto", () -> "none");
    }
    
    @Autowired
    private ConnectorRepository connectorRepository;
    
    @Autowired
    private ConnectorTargetRepository targetRepository;
    
    @Autowired
    private ConnectorScheduleRepository scheduleRepository;
    
    @Autowired
    private ConnectorRunRepository runRepository;
    
    @Autowired
    private WebhookEventRepository webhookEventRepository;
    
    @Autowired
    private ConnectorCredentialRepository credentialRepository;
    
    @Autowired
    private OutboxEventRepository outboxRepository;
    
    @Autowired
    private SchedulerService schedulerService;
    
    @Autowired
    private WebhookService webhookService;
    
    @Autowired
    private CredentialService credentialService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private UUID tenantId;
    private UUID connectorId;
    private UUID targetId;
    
    @BeforeEach
    void setUp() {
        // Clean up
        runRepository.deleteAll();
        webhookEventRepository.deleteAll();
        scheduleRepository.deleteAll();
        credentialRepository.deleteAll();
        targetRepository.deleteAll();
        connectorRepository.deleteAll();
        outboxRepository.deleteAll();
        
        // Setup test data
        tenantId = UUID.randomUUID();
        
        // Create connector
        Connector connector = new Connector();
        connector.setTenantId(tenantId);
        connector.setConnectorName("Test Connector");
        connector.setConnectorType("AUDIT");
        connector.setStatus("ACTIVE");
        connector.setBaseUrl("https://test.example.com");
        connector.setAuthType("BEARER");
        connector.setMetadata(Map.of("test", "data"));
        connector = connectorRepository.save(connector);
        connectorId = connector.getConnectorId();
        
        // Create target
        ConnectorTarget target = new ConnectorTarget();
        target.setTenantId(tenantId);
        target.setConnectorId(connectorId);
        target.setTargetKey("test-target");
        target.setTargetType("AUDIT");
        target.setSubjectType("USER");
        target.setSupportedActions(List.of("ACCESS", "EXPORT"));
        target.setRequiresApproval(false);
        target.setMetadata(Map.of());
        target = targetRepository.save(target);
        targetId = target.getTargetId();
    }
    
    @Test
    void testSchedulerIdempotency() {
        // Create a schedule
        ConnectorSchedule schedule = schedulerService.createSchedule(
            tenantId,
            connectorId,
            targetId,
            "AUDIT_PULL",
            null,
            60 // Run every 60 seconds
        );
        
        assertNotNull(schedule.getScheduleId());
        assertTrue(schedule.isEnabled());
        assertNotNull(schedule.getNextRunAt());
        
        // Set next run time to past to make it due
        schedule.setNextRunAt(Instant.now().minusSeconds(10));
        scheduleRepository.save(schedule);
        
        Instant fireTime = Instant.now();
        
        // Process schedules - should create one run
        schedulerService.processDueSchedules();
        
        List<ConnectorRun> runs1 = runRepository.findByScheduleIdOrderByCreatedAtDesc(schedule.getScheduleId());
        assertThat(runs1).hasSize(1);
        
        ConnectorRun run1 = runs1.get(0);
        assertEquals("SCHEDULED", run1.getRunType());
        assertEquals("PENDING", run1.getStatus());
        assertEquals(schedule.getScheduleId(), run1.getScheduleId());
        assertNotNull(run1.getIdempotencyKey());
        
        // Process again - should NOT create duplicate run (idempotency)
        schedulerService.processDueSchedules();
        
        List<ConnectorRun> runs2 = runRepository.findByScheduleIdOrderByCreatedAtDesc(schedule.getScheduleId());
        assertThat(runs2).hasSize(1); // Still only 1 run
        
        // Verify outbox events created
        long outboxCount = outboxRepository.count();
        assertTrue(outboxCount >= 1); // At least one outbox event
    }
    
    @Test
    void testWebhookSignatureVerification() {
        // Store webhook secret
        String webhookSecret = "test-webhook-secret-123";
        credentialService.storeCredential(tenantId, connectorId, "HMAC_SECRET", webhookSecret);
        
        // Create webhook payload
        Map<String, Object> payload = Map.of(
            "action", "push",
            "repository", "test-repo",
            "timestamp", System.currentTimeMillis()
        );
        
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        // Calculate correct signature
        String correctSignature = "sha256=" + WebhookSignatureVerifier.calculateHmacSha256(payloadJson, webhookSecret);
        
        // Process webhook with correct signature
        WebhookEvent event = webhookService.processWebhook(
            tenantId,
            connectorId,
            "github",
            correctSignature,
            payloadJson
        );
        
        assertNotNull(event.getWebhookEventId());
        assertEquals("github", event.getProvider());
        assertTrue(event.isSignatureVerified());
        assertEquals(correctSignature, event.getSignature());
        
        // Verify connector run created for webhook
        List<ConnectorRun> runs = runRepository.findAll();
        assertThat(runs).hasSize(1);
        
        ConnectorRun run = runs.get(0);
        assertEquals("WEBHOOK", run.getRunType());
        assertEquals(event.getWebhookEventId(), run.getWebhookEventId());
        
        // Test with incorrect signature
        String incorrectSignature = "sha256=wrong-signature";
        
        WebhookEvent event2 = webhookService.processWebhook(
            tenantId,
            connectorId,
            "github",
            incorrectSignature,
            payloadJson
        );
        
        assertNotNull(event2.getWebhookEventId());
        assertFalse(event2.isSignatureVerified());
        
        // Verify outbox events
        long outboxCount = outboxRepository.count();
        assertTrue(outboxCount >= 2); // At least 2 webhook events
    }
    
    @Test
    void testConnectorRunReceipts() {
        // Create a schedule
        ConnectorSchedule schedule = schedulerService.createSchedule(
            tenantId,
            connectorId,
            targetId,
            "EXPORT",
            null,
            120
        );
        
        // Set schedule to be due
        schedule.setNextRunAt(Instant.now().minusSeconds(5));
        scheduleRepository.save(schedule);
        
        // Process and create run
        schedulerService.processDueSchedules();
        
        List<ConnectorRun> runs = runRepository.findByScheduleIdOrderByCreatedAtDesc(schedule.getScheduleId());
        assertThat(runs).hasSize(1);
        
        ConnectorRun run = runs.get(0);
        
        // Simulate job execution and store receipt
        run.setStatus("RUNNING");
        run.setStartedAt(Instant.now());
        runRepository.save(run);
        
        // Complete job with receipt
        run.setStatus("COMPLETED");
        run.setFinishedAt(Instant.now());
        run.setReceipt(Map.of(
            "records_processed", 100,
            "files_exported", 5,
            "duration_ms", 1500
        ));
        runRepository.save(run);
        
        // Verify receipt stored correctly
        ConnectorRun savedRun = runRepository.findById(run.getRunId()).orElseThrow();
        assertEquals("COMPLETED", savedRun.getStatus());
        assertNotNull(savedRun.getReceipt());
        assertEquals(100, savedRun.getReceipt().get("records_processed"));
        assertEquals(5, savedRun.getReceipt().get("files_exported"));
    }
    
    @Test
    void testCredentialResolvers() {
        // Test local DB credential storage
        String apiKey = "test-api-key-12345";
        
        credentialService.storeCredential(tenantId, connectorId, "API_KEY", apiKey);
        
        // Verify stored
        assertTrue(credentialService.credentialExists(tenantId, connectorId, "API_KEY"));
        
        // Resolve credential
        var resolved = credentialService.resolveCredential(tenantId, connectorId, "API_KEY");
        
        assertEquals("API_KEY", resolved.credentialType());
        assertEquals(apiKey, resolved.value());
        assertEquals("local_db", resolved.metadata().get("source"));
        
        // Verify encrypted in database
        var storedCredential = credentialRepository
            .findByConnectorIdAndCredentialType(connectorId, "API_KEY")
            .orElseThrow();
        
        assertNotNull(storedCredential.getEncryptedValue());
        assertTrue(storedCredential.getEncryptedValue().length > 0);
        assertEquals("master_key_v1", storedCredential.getEncryptionKeyRef());
        
        // Delete credential
        credentialService.deleteCredential(tenantId, connectorId, "API_KEY");
        
        assertFalse(credentialService.credentialExists(tenantId, connectorId, "API_KEY"));
    }
    
    @Test
    void testWebhookEventNormalization() {
        String payload = """
            {
                "action": "pull_request",
                "repository": "test-repo",
                "number": 42
            }
            """;
        
        WebhookEvent event = webhookService.processWebhook(
            tenantId,
            connectorId,
            "github",
            null,
            payload
        );
        
        assertEquals("github", event.getProvider());
        assertEquals("pull_request", event.getEventType());
        assertEquals("DATA_CHANGED", event.getNormalizedEventType());
        assertNotNull(event.getRawPayload());
        assertEquals(42, event.getRawPayload().get("number"));
    }
    
    @Test
    void testScheduleWithCronExpression() {
        // Create schedule with cron expression
        ConnectorSchedule schedule = schedulerService.createSchedule(
            tenantId,
            connectorId,
            targetId,
            "SYNC",
            "0 0 * * * *", // Every hour
            null
        );
        
        assertNotNull(schedule.getScheduleId());
        assertEquals("0 0 * * * *", schedule.getCronExpr());
        assertNull(schedule.getIntervalSeconds());
        assertTrue(schedule.isEnabled());
    }
    
    @Test
    void testMultipleSchedulesForDifferentConnectors() {
        // Create second connector
        Connector connector2 = new Connector();
        connector2.setTenantId(tenantId);
        connector2.setConnectorName("Test Connector 2");
        connector2.setConnectorType("EXPORT");
        connector2.setStatus("ACTIVE");
        connector2.setBaseUrl("https://test2.example.com");
        connector2.setAuthType("BEARER");
        connector2.setMetadata(Map.of("test", "data"));
        connector2 = connectorRepository.save(connector2);
        UUID connector2Id = connector2.getConnectorId();
        
        // Create second target
        ConnectorTarget target2 = new ConnectorTarget();
        target2.setTenantId(tenantId);
        target2.setConnectorId(connector2Id);
        target2.setTargetKey("test-target-2");
        target2.setTargetType("EXPORT");
        target2.setSubjectType("USER");
        target2.setSupportedActions(List.of("EXPORT"));
        target2.setRequiresApproval(false);
        target2.setMetadata(Map.of());
        target2 = targetRepository.save(target2);
        UUID target2Id = target2.getTargetId();
        
        // Create schedules for different connectors
        ConnectorSchedule schedule1 = schedulerService.createSchedule(
            tenantId, connectorId, targetId, "AUDIT_PULL", null, 60
        );
        
        ConnectorSchedule schedule2 = schedulerService.createSchedule(
            tenantId, connector2Id, target2Id, "EXPORT", null, 120
        );
        
        // Make both due
        schedule1.setNextRunAt(Instant.now().minusSeconds(5));
        schedule2.setNextRunAt(Instant.now().minusSeconds(5));
        scheduleRepository.saveAll(List.of(schedule1, schedule2));
        
        // Process schedules
        schedulerService.processDueSchedules();
        
        // Verify runs created for both
        List<ConnectorRun> runs = runRepository.findAll();
        assertThat(runs).hasSize(2);
        
        // Verify runs are for different connectors
        assertThat(runs)
            .extracting(ConnectorRun::getConnectorId)
            .containsExactlyInAnyOrder(connectorId, connector2Id);
    }
}
