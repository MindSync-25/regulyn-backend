package io.regulyn.connector.credentials;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.events.outbox.OutboxEvent;
import com.regulyn.events.outbox.OutboxEventRepository;
import io.regulyn.connector.AbstractAwsIntegrationTestBase;
import io.regulyn.connector.config.CredentialConfig;
import io.regulyn.connector.model.ConnectorCredential;
import io.regulyn.connector.repository.ConnectorCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for credential resolution service.
 * Tests LOCAL_DB_ENCRYPTED, ENV, and AWS_SECRETS_MANAGER providers.
 */
class CredentialResolutionIntegrationTest extends AbstractAwsIntegrationTestBase {

    @Autowired
    private CredentialResolutionService resolutionService;

    @Autowired
    private ConnectorCredentialRepository credentialRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private SecretsManagerClient secretsManagerClient;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CredentialConfig credentialConfig;

    private UUID tenantId;
    private UUID connectorId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        connectorId = createTestConnector(tenantId);
    }

    private UUID createTestConnector(UUID tenantId) {
        UUID connectorId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO connector.connectors (connector_id, tenant_id, connector_name, connector_type, status, auth_type, metadata, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb, NOW(), NOW())",
            connectorId, tenantId, "Test Connector", "REST_API", "ACTIVE", "API_KEY"
        );
        return connectorId;
    }

    @Test
    void testLocalDbEncryptedRoundtrip() throws Exception {
        // Prepare credentials
        Map<String, String> originalCreds = new HashMap<>();
        originalCreds.put("client_id", "test-client-123");
        originalCreds.put("client_secret", "super-secret-value");
        originalCreds.put("api_key", "api-key-xyz");

        byte[] jsonBytes = objectMapper.writeValueAsBytes(originalCreds);
        EncryptionService.EncryptedData encrypted = encryptionService.encrypt(jsonBytes);

        // Store in database (convert base64 strings back to bytes for DB storage)
        ConnectorCredential credential = new ConnectorCredential();
        credential.setTenantId(tenantId);
        credential.setConnectorId(connectorId);
        credential.setProvider(ConnectorCredential.CredentialProvider.LOCAL_DB_ENCRYPTED);
        credential.setEncPayload(java.util.Base64.getDecoder().decode(encrypted.getEncPayload()));
        credential.setEncIv(java.util.Base64.getDecoder().decode(encrypted.getIv()));
        credential.setEncKid(null);
        credentialRepository.save(credential);

        // Resolve credentials
        String correlationId = UUID.randomUUID().toString();
        ResolvedCredentials resolved = resolutionService.resolve(tenantId, connectorId, correlationId);

        // Assertions
        assertNotNull(resolved);
        assertEquals("LOCAL_DB_ENCRYPTED", resolved.getProvider());
        assertEquals("test-client-123", resolved.get("client_id"));
        assertEquals("super-secret-value", resolved.get("client_secret"));
        assertEquals("api-key-xyz", resolved.get("api_key"));
        assertTrue(resolved.has("client_id"));
        assertEquals(3, resolved.getKeys().size());

        // Verify outbox events
        List<OutboxEvent> allEvents = outboxRepository.findAll();
        boolean hasSuccessEvent = allEvents.stream()
            .filter(e -> e.getEventType().equals("CREDENTIAL_RESOLVE_SUCCEEDED"))
            .anyMatch(e -> e.getCorrelationId().equals(correlationId));
        assertTrue(hasSuccessEvent, "Should have SUCCEEDED outbox event");
    }

    @Test
    void testEnvProviderResolution() throws Exception {
        // Set up environment variable
        String testEnvVarName = "TEST_CONNECTOR_CLIENT_ID_" + UUID.randomUUID().toString().replace("-", "");
        String testEnvVarValue = "env-client-id-123";
        
        // Note: Can't set env vars at runtime in Java, so we'll use a workaround
        // In production, this would work with real env vars
        // For this test, we'll store the actual value and expect failure with proper error code
        
        // Prepare env var mapping
        Map<String, String> envVarMapping = new HashMap<>();
        envVarMapping.put("client_id", testEnvVarName);
        envVarMapping.put("api_key", "NONEXISTENT_API_KEY");

        byte[] jsonBytes = objectMapper.writeValueAsBytes(envVarMapping);
        EncryptionService.EncryptedData encrypted = encryptionService.encrypt(jsonBytes);

        // Store in database (convert base64 strings back to bytes for DB storage)
        ConnectorCredential credential = new ConnectorCredential();
        credential.setTenantId(tenantId);
        credential.setConnectorId(connectorId);
        credential.setProvider(ConnectorCredential.CredentialProvider.ENV);
        credential.setEncPayload(java.util.Base64.getDecoder().decode(encrypted.getEncPayload()));
        credential.setEncIv(java.util.Base64.getDecoder().decode(encrypted.getIv()));
        credentialRepository.save(credential);

        // Resolve should fail because env var doesn't exist
        String correlationId = UUID.randomUUID().toString();
        CredentialResolutionService.CredentialResolutionException exception = assertThrows(
            CredentialResolutionService.CredentialResolutionException.class,
            () -> resolutionService.resolve(tenantId, connectorId, correlationId)
        );

        assertEquals("ENV_VAR_NOT_FOUND", exception.getErrorCode());

        // Verify failure outbox event
        List<OutboxEvent> allEvents = outboxRepository.findAll();
        assertTrue(allEvents.stream()
            .filter(e -> e.getEventType().equals("CREDENTIAL_RESOLVE_FAILED"))
            .anyMatch(e -> e.getCorrelationId().equals(correlationId)));
    }

    @Test
    void testAwsSecretsManagerWithLocalStack() throws Exception {
        // Create secret in LocalStack
        String secretName = "test-connector-secret-" + UUID.randomUUID();
        Map<String, String> secretValue = new HashMap<>();
        secretValue.put("client_id", "aws-client-123");
        secretValue.put("client_secret", "aws-secret-456");
        secretValue.put("api_token", "aws-token-789");

        String secretJson = objectMapper.writeValueAsString(secretValue);
        secretsManagerClient.createSecret(CreateSecretRequest.builder()
                .name(secretName)
                .secretString(secretJson)
                .build());

        // Store secret metadata in database
        Map<String, String> secretMetadata = new HashMap<>();
        secretMetadata.put("secretId", secretName);

        byte[] jsonBytes = objectMapper.writeValueAsBytes(secretMetadata);
        EncryptionService.EncryptedData encrypted = encryptionService.encrypt(jsonBytes);

        ConnectorCredential credential = new ConnectorCredential();
        credential.setTenantId(tenantId);
        credential.setConnectorId(connectorId);
        credential.setProvider(ConnectorCredential.CredentialProvider.AWS_SECRETS_MANAGER);
        credential.setEncPayload(java.util.Base64.getDecoder().decode(encrypted.getEncPayload()));
        credential.setEncIv(java.util.Base64.getDecoder().decode(encrypted.getIv()));
        credentialRepository.save(credential);

        // Resolve credentials
        String correlationId = UUID.randomUUID().toString();
        ResolvedCredentials resolved = resolutionService.resolve(tenantId, connectorId, correlationId);

        // Assertions
        assertNotNull(resolved);
        assertEquals("AWS_SECRETS_MANAGER", resolved.getProvider());
        assertEquals("aws-client-123", resolved.get("client_id"));
        assertEquals("aws-secret-456", resolved.get("client_secret"));
        assertEquals("aws-token-789", resolved.get("api_token"));

        // Verify outbox events
        List<OutboxEvent> allEvents = outboxRepository.findAll();
        assertTrue(allEvents.stream()
            .filter(e -> e.getEventType().equals("CREDENTIAL_RESOLVE_SUCCEEDED"))
            .anyMatch(e -> e.getCorrelationId().equals(correlationId)));
    }

    @Test
    void testAwsSecretsManagerCaching() throws Exception {
        // Create secret in LocalStack
        String secretName = "test-cache-secret-" + UUID.randomUUID();
        Map<String, String> secretValue = new HashMap<>();
        secretValue.put("cached_key", "cached_value");

        String secretJson = objectMapper.writeValueAsString(secretValue);
        secretsManagerClient.createSecret(CreateSecretRequest.builder()
                .name(secretName)
                .secretString(secretJson)
                .build());

        // Store secret metadata
        Map<String, String> secretMetadata = new HashMap<>();
        secretMetadata.put("secretId", secretName);

        byte[] jsonBytes = objectMapper.writeValueAsBytes(secretMetadata);
        EncryptionService.EncryptedData encrypted = encryptionService.encrypt(jsonBytes);

        ConnectorCredential credential = new ConnectorCredential();
        credential.setTenantId(tenantId);
        credential.setConnectorId(connectorId);
        credential.setProvider(ConnectorCredential.CredentialProvider.AWS_SECRETS_MANAGER);
        credential.setEncPayload(java.util.Base64.getDecoder().decode(encrypted.getEncPayload()));
        credential.setEncIv(java.util.Base64.getDecoder().decode(encrypted.getIv()));
        credentialRepository.save(credential);

        // First resolution - cache miss
        String correlationId1 = UUID.randomUUID().toString();
        ResolvedCredentials resolved1 = resolutionService.resolve(tenantId, connectorId, correlationId1);
        assertEquals("cached_value", resolved1.get("cached_key"));

        // Second resolution - cache hit (should not call AWS again)
        String correlationId2 = UUID.randomUUID().toString();
        ResolvedCredentials resolved2 = resolutionService.resolve(tenantId, connectorId, correlationId2);
        assertEquals("cached_value", resolved2.get("cached_key"));

        // Both should succeed
        assertEquals(resolved1.get("cached_key"), resolved2.get("cached_key"));

        // Verify both resolutions created outbox events
        List<OutboxEvent> allEvents = outboxRepository.findAll();
        assertTrue(allEvents.stream()
            .filter(e -> e.getEventType().equals("CREDENTIAL_RESOLVE_SUCCEEDED"))
            .anyMatch(e -> e.getCorrelationId().equals(correlationId1)));
        assertTrue(allEvents.stream()
            .filter(e -> e.getEventType().equals("CREDENTIAL_RESOLVE_SUCCEEDED"))
            .anyMatch(e -> e.getCorrelationId().equals(correlationId2)));
    }

    @Test
    void testCredentialNotFound() {
        String correlationId = UUID.randomUUID().toString();
        UUID nonExistentConnectorId = UUID.randomUUID();

        CredentialResolutionService.CredentialResolutionException exception = assertThrows(
            CredentialResolutionService.CredentialResolutionException.class,
            () -> resolutionService.resolve(tenantId, nonExistentConnectorId, correlationId)
        );

        assertEquals("CREDENTIAL_NOT_FOUND", exception.getErrorCode());

        // Verify failure outbox event
        List<OutboxEvent> allEvents = outboxRepository.findAll();
        assertTrue(allEvents.stream()
            .filter(e -> e.getEventType().equals("CREDENTIAL_RESOLVE_FAILED"))
            .anyMatch(e -> e.getCorrelationId().equals(correlationId)));
    }

    @Test
    void testOutboxEventDoesNotContainSecretValues() throws Exception {
        // Create credentials
        Map<String, String> secretCreds = new HashMap<>();
        secretCreds.put("password", "super-secret-password-123");
        secretCreds.put("api_key", "ultra-secret-api-key");

        byte[] jsonBytes = objectMapper.writeValueAsBytes(secretCreds);
        EncryptionService.EncryptedData encrypted = encryptionService.encrypt(jsonBytes);

        ConnectorCredential credential = new ConnectorCredential();
        credential.setTenantId(tenantId);
        credential.setConnectorId(connectorId);
        credential.setProvider(ConnectorCredential.CredentialProvider.LOCAL_DB_ENCRYPTED);
        credential.setEncPayload(java.util.Base64.getDecoder().decode(encrypted.getEncPayload()));
        credential.setEncIv(java.util.Base64.getDecoder().decode(encrypted.getIv()));
        credentialRepository.save(credential);

        String correlationId = UUID.randomUUID().toString();
        resolutionService.resolve(tenantId, connectorId, correlationId);

        // Verify outbox events do NOT contain secret values
        List<OutboxEvent> allEvents = outboxRepository.findAll();
        List<OutboxEvent> successEvents = allEvents.stream()
            .filter(e -> e.getEventType().equals("CREDENTIAL_RESOLVE_SUCCEEDED"))
            .toList();
        for (OutboxEvent event : successEvents) {
            String payloadJson = event.getPayload();
            assertFalse(payloadJson.contains("super-secret-password-123"), "Outbox should not contain password value");
            assertFalse(payloadJson.contains("ultra-secret-api-key"), "Outbox should not contain api_key value");
        }
    }

    @Test
    void testAwsDisabledFailClosed() throws Exception {
        // Scenario: connector_credentials.provider = AWS_SECRETS_MANAGER
        // BUT connector.credentials.aws.enabled = false (AWS client not injected)
        // Expected: FAIL-CLOSED with AWS_DISABLED error, NO fallback to other providers

        // Create AWS credential with secretId metadata
        Map<String, String> secretMetadata = new HashMap<>();
        secretMetadata.put("secretId", "my-secret");
        secretMetadata.put("versionId", "v1");

        byte[] jsonBytes = objectMapper.writeValueAsBytes(secretMetadata);
        EncryptionService.EncryptedData encrypted = encryptionService.encrypt(jsonBytes);

        ConnectorCredential credential = new ConnectorCredential();
        credential.setTenantId(tenantId);
        credential.setConnectorId(connectorId);
        credential.setProvider(ConnectorCredential.CredentialProvider.AWS_SECRETS_MANAGER);
        credential.setEncPayload(java.util.Base64.getDecoder().decode(encrypted.getEncPayload()));
        credential.setEncIv(java.util.Base64.getDecoder().decode(encrypted.getIv()));
        credentialRepository.save(credential);

        // Temporarily disable AWS by creating a new service with empty Optional
        CredentialResolutionService serviceWithoutAws = new CredentialResolutionService(
            credentialRepository,
            encryptionService,
            Optional.empty(), // AWS disabled
            outboxRepository,
            jdbcTemplate,
            objectMapper
        );

        String correlationId = UUID.randomUUID().toString();

        // Resolution MUST fail with AWS_DISABLED error code
        CredentialResolutionService.CredentialResolutionException exception = assertThrows(
            CredentialResolutionService.CredentialResolutionException.class,
            () -> serviceWithoutAws.resolve(tenantId, connectorId, correlationId)
        );

        // Verify FAIL-CLOSED behavior
        assertEquals("AWS_DISABLED", exception.getErrorCode(), 
            "FAIL-CLOSED: Must throw AWS_DISABLED when AWS not enabled");
        assertTrue(exception.getMessage().contains("AWS Secrets Manager is not enabled"),
            "Error message should indicate AWS is disabled");

        // Verify outbox event written with AWS_DISABLED error code
        List<OutboxEvent> allEvents = outboxRepository.findAll();
        OutboxEvent failedEvent = allEvents.stream()
            .filter(e -> e.getEventType().equals("CREDENTIAL_RESOLVE_FAILED"))
            .filter(e -> e.getCorrelationId().equals(correlationId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("CREDENTIAL_RESOLVE_FAILED event not found"));

        String payload = failedEvent.getPayload();
        assertTrue(payload.contains("AWS_DISABLED"), 
            "Outbox event must contain AWS_DISABLED error code");
        
        // CRITICAL: Verify NO silent fallback occurred
        // There should be NO SUCCEEDED event for this correlationId
        boolean hasSuccessEvent = allEvents.stream()
            .filter(e -> e.getEventType().equals("CREDENTIAL_RESOLVE_SUCCEEDED"))
            .anyMatch(e -> e.getCorrelationId().equals(correlationId));
        assertFalse(hasSuccessEvent, 
            "FAIL-CLOSED: Must NOT fallback to ENV or LOCAL_DB_ENCRYPTED silently");
    }
}
