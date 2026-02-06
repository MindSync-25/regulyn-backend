package io.regulyn.connector.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.events.outbox.OutboxEvent;
import com.regulyn.events.outbox.OutboxEventRepository;
import io.regulyn.connector.AbstractIntegrationTestBase;
import io.regulyn.connector.credentials.CredentialResolutionService;
import io.regulyn.connector.credentials.EncryptionService;
import io.regulyn.connector.model.Connector;
import io.regulyn.connector.model.ConnectorCredential;
import io.regulyn.connector.model.WebhookEvent;
import io.regulyn.connector.repository.ConnectorCredentialRepository;
import io.regulyn.connector.repository.ConnectorRepository;
import io.regulyn.connector.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for webhook receiver infrastructure.
 * Tests signature verification, normalization, persistence, and idempotency.
 */
class WebhookIntegrationTest extends AbstractIntegrationTestBase {

    @DynamicPropertySource
    static void configureWebhookProperties(DynamicPropertyRegistry registry) {
        // Generate test encryption key
        String testKey = EncryptionService.generateKey();
        registry.add("connector.credentials.encryption.key", () -> testKey);
        
        // Disable AWS for webhook tests
        registry.add("connector.credentials.aws.enabled", () -> "false");
    }

    @Autowired
    private io.regulyn.connector.webhook.WebhookService webhookService;

    @Autowired
    private io.regulyn.connector.webhook.WebhookSignatureVerifier signatureVerifier;

    @Autowired
    private io.regulyn.connector.webhook.WebhookNormalizer normalizer;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private ConnectorCredentialRepository credentialRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private CredentialResolutionService credentialResolutionService;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID tenantId;
    private UUID connectorId;
    private String webhookSecret;

    @BeforeEach
    void setUp() {
        // Clean up tables
        jdbcTemplate.execute("DELETE FROM connector.outbox_events");
        jdbcTemplate.execute("DELETE FROM connector.webhook_events");
        jdbcTemplate.execute("DELETE FROM connector.connector_credentials");
        jdbcTemplate.execute("DELETE FROM connector.connectors");

        // Create test tenant and connector
        tenantId = UUID.randomUUID();
        connectorId = UUID.randomUUID();
        webhookSecret = "test_webhook_secret_12345";

        Connector connector = new Connector();
        connector.setTenantId(tenantId);
        connector.setConnectorName("test-connector");
        connector.setConnectorType("GITHUB");
        connector.setStatus("ACTIVE");
        connector.setAuthType("API_KEY"); // Required field
        connector.setCreatedAt(Instant.now());
        connector.setUpdatedAt(Instant.now());
        Connector savedConnector = connectorRepository.save(connector);
        connectorId = savedConnector.getConnectorId();

        // Create credentials with webhook_secret
        createCredentialWithWebhookSecret();
    }

    private void createCredentialWithWebhookSecret() {
        try {
            Map<String, Object> credentials = new HashMap<>();
            credentials.put("webhook_secret", webhookSecret);

            String jsonCredentials = objectMapper.writeValueAsString(credentials);
            EncryptionService.EncryptedData encryptedData = encryptionService.encrypt(jsonCredentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            // Convert base64 strings to byte arrays
            byte[] ivBytes = java.util.Base64.getDecoder().decode(encryptedData.getIv());
            byte[] encryptedBytes = java.util.Base64.getDecoder().decode(encryptedData.getEncPayload());

            ConnectorCredential credential = new ConnectorCredential();
            credential.setId(UUID.randomUUID());
            credential.setTenantId(tenantId);
            credential.setConnectorId(connectorId);
            credential.setProvider(io.regulyn.connector.model.ConnectorCredential.CredentialProvider.LOCAL_DB_ENCRYPTED);
            credential.setEncIv(ivBytes);
            credential.setEncPayload(encryptedBytes);
            credential.setCreatedAt(Instant.now());
            credential.setUpdatedAt(Instant.now());
            credentialRepository.save(credential);

        } catch (Exception e) {
            throw new RuntimeException("Failed to create test credentials", e);
        }
    }

    @Test
    void testGithubSignatureValid_persistsWebhookEvent_andEmitsOutbox() throws Exception {
        // Arrange
        String payload = "{\"action\":\"opened\",\"repository\":{\"full_name\":\"octocat/Hello-World\"}}";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        String signature = computeGitHubSignature(payloadBytes, webhookSecret);

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Hub-Signature-256", "sha256=" + signature);
        headers.put("X-GitHub-Event", "pull_request");
        headers.put("X-Correlation-Id", "test-correlation-123");

        // Act
        io.regulyn.connector.webhook.WebhookService.WebhookProcessingResult result = 
                webhookService.processWebhook("github", connectorId, headers, payloadBytes, "test-correlation-123");

        // Assert
        assertTrue(result.isSignatureValid(), "Signature should be valid");
        assertFalse(result.isDuplicate(), "Should not be duplicate");
        assertEquals("test-correlation-123", result.getCorrelationId());

        // Verify webhook_events row
        List<WebhookEvent> events = webhookEventRepository.findByTenantIdAndConnectorIdOrderByReceivedAtDesc(
                tenantId, connectorId, org.springframework.data.domain.PageRequest.of(0, 10));
        assertEquals(1, events.size());
        
        WebhookEvent event = events.get(0);
        assertEquals(tenantId, event.getTenantId());
        assertEquals(connectorId, event.getConnectorId());
        assertEquals("github", event.getProvider());
        assertTrue(event.getSignatureValid());
        assertEquals("test-correlation-123", event.getCorrelationId());
        assertEquals("github.pull_request", event.getNormalizedType());
        assertEquals("octocat/Hello-World", event.getNormalizedSubject());
        assertNotNull(event.getPayloadHash());

        // Verify outbox events (WEBHOOK_RECEIVED, WEBHOOK_NORMALIZED)
        List<OutboxEvent> outboxEvents = outboxRepository.findAll();
        assertTrue(outboxEvents.size() >= 2, "Should have at least WEBHOOK_RECEIVED and WEBHOOK_NORMALIZED events");
        
        boolean hasReceivedEvent = outboxEvents.stream()
                .anyMatch(e -> e.getEventType().equals("WEBHOOK_RECEIVED"));
        boolean hasNormalizedEvent = outboxEvents.stream()
                .anyMatch(e -> e.getEventType().equals("WEBHOOK_NORMALIZED"));
        
        assertTrue(hasReceivedEvent, "Should emit WEBHOOK_RECEIVED event");
        assertTrue(hasNormalizedEvent, "Should emit WEBHOOK_NORMALIZED event");
    }

    @Test
    void testGithubSignatureInvalid_persistsWithFalse_andEmitsInvalidEvent() throws Exception {
        // Arrange
        String payload = "{\"action\":\"opened\"}";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        String invalidSignature = "invalid_signature_123";

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Hub-Signature-256", "sha256=" + invalidSignature);
        headers.put("X-GitHub-Event", "push");
        headers.put("X-Correlation-Id", "test-invalid-sig");

        // Act
        io.regulyn.connector.webhook.WebhookService.WebhookProcessingResult result = 
                webhookService.processWebhook("github", connectorId, headers, payloadBytes, "test-invalid-sig");

        // Assert
        assertFalse(result.isSignatureValid(), "Signature should be invalid");
        assertFalse(result.isDuplicate());

        // Verify webhook_events row with signature_valid=false
        List<WebhookEvent> events = webhookEventRepository.findByTenantIdAndConnectorIdOrderByReceivedAtDesc(
                tenantId, connectorId, org.springframework.data.domain.PageRequest.of(0, 10));
        assertEquals(1, events.size());
        
        WebhookEvent event = events.get(0);
        assertFalse(event.getSignatureValid(), "Should store with signature_valid=false");

        // Verify WEBHOOK_SIGNATURE_INVALID outbox event
        List<OutboxEvent> outboxEvents = outboxRepository.findAll();
        boolean hasInvalidEvent = outboxEvents.stream()
                .anyMatch(e -> e.getEventType().equals("WEBHOOK_SIGNATURE_INVALID"));
        
        assertTrue(hasInvalidEvent, "Should emit WEBHOOK_SIGNATURE_INVALID event");
    }

    @Test
    void testPayloadHashComputedCorrectly() throws Exception {
        // Arrange
        String payload = "{\"test\":\"data\"}";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        String expectedHash = signatureVerifier.computePayloadHash(payloadBytes);

        String signature = computeGitHubSignature(payloadBytes, webhookSecret);
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Hub-Signature-256", "sha256=" + signature);
        headers.put("X-Correlation-Id", "test-hash-123");

        // Act
        webhookService.processWebhook("github", connectorId, headers, payloadBytes, "test-hash-123");

        // Assert
        List<WebhookEvent> events = webhookEventRepository.findByTenantIdAndConnectorIdOrderByReceivedAtDesc(
                tenantId, connectorId, org.springframework.data.domain.PageRequest.of(0, 10));
        assertEquals(1, events.size());
        
        WebhookEvent event = events.get(0);
        assertEquals(expectedHash, event.getPayloadHash(), "Payload hash should match computed SHA-256 hex");
    }

    @Test
    void testDuplicateWebhookWithinWindow_doesNotEmitDuplicateOutbox() throws Exception {
        // Arrange
        String payload = "{\"action\":\"created\"}";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        String signature = computeGitHubSignature(payloadBytes, webhookSecret);

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Hub-Signature-256", "sha256=" + signature);
        headers.put("X-Correlation-Id", "test-dup-1");

        // Act - First webhook
        io.regulyn.connector.webhook.WebhookService.WebhookProcessingResult result1 = 
                webhookService.processWebhook("github", connectorId, headers, payloadBytes, "test-dup-1");
        
        int outboxCountAfterFirst = outboxRepository.findAll().size();

        // Act - Duplicate webhook (same payload hash, within 10 minutes)
        headers.put("X-Correlation-Id", "test-dup-2");
        io.regulyn.connector.webhook.WebhookService.WebhookProcessingResult result2 = 
                webhookService.processWebhook("github", connectorId, headers, payloadBytes, "test-dup-2");

        // Assert
        assertFalse(result1.isDuplicate(), "First webhook should not be duplicate");
        assertTrue(result2.isDuplicate(), "Second webhook should be detected as duplicate");

        // Verify outbox events NOT emitted for duplicate
        int outboxCountAfterSecond = outboxRepository.findAll().size();
        assertEquals(outboxCountAfterFirst, outboxCountAfterSecond, 
                "No new outbox events should be emitted for duplicate webhook");

        // Verify only one webhook_events row persisted
        List<WebhookEvent> events = webhookEventRepository.findByTenantIdAndConnectorIdOrderByReceivedAtDesc(
                tenantId, connectorId, org.springframework.data.domain.PageRequest.of(0, 10));
        assertEquals(1, events.size(), "Duplicate webhook should not create second row");
    }

    @Test
    void testNormalizationSetsTypeAndSubjectForKnownProvider() throws Exception {
        // Arrange
        String payload = "{\"type\":\"customer.created\",\"data\":{\"object\":{\"id\":\"cus_123\"}}}";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        String signature = computeGitHubSignature(payloadBytes, webhookSecret); // Using same secret for simplicity

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Webhook-Signature", signature);
        headers.put("Stripe-Signature", "t=1234567890,v1=" + signature); // Stripe format
        headers.put("X-Correlation-Id", "test-norm-123");

        // Act
        webhookService.processWebhook("stripe", connectorId, headers, payloadBytes, "test-norm-123");

        // Assert
        List<WebhookEvent> events = webhookEventRepository.findByTenantIdAndConnectorIdOrderByReceivedAtDesc(
                tenantId, connectorId, org.springframework.data.domain.PageRequest.of(0, 10));
        assertEquals(1, events.size());
        
        WebhookEvent event = events.get(0);
        assertEquals("stripe.customer.created", event.getNormalizedType(), "Should normalize Stripe event type");
        assertEquals("cus_123", event.getNormalizedSubject(), "Should extract customer ID as subject");

        // Verify WEBHOOK_NORMALIZED event emitted
        List<OutboxEvent> outboxEvents = outboxRepository.findAll();
        boolean hasNormalizedEvent = outboxEvents.stream()
                .anyMatch(e -> e.getEventType().equals("WEBHOOK_NORMALIZED"));
        
        assertTrue(hasNormalizedEvent, "Should emit WEBHOOK_NORMALIZED event for known provider");
    }

    // Helper method to compute GitHub HMAC-SHA256 signature
    private String computeGitHubSignature(byte[] payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] hmacBytes = mac.doFinal(payload);
        
        // Convert to hex
        StringBuilder hexString = new StringBuilder();
        for (byte b : hmacBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
