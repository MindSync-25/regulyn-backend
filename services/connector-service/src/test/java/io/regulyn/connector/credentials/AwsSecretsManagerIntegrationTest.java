package io.regulyn.connector.credentials;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.regulyn.connector.ConnectorServiceApplication;
import io.regulyn.connector.model.ConnectorCredential;
import io.regulyn.connector.repository.ConnectorCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SECRETSMANAGER;

/**
 * Integration tests for AWS Secrets Manager credential resolver using LocalStack.
 * NO REAL AWS ACCOUNT REQUIRED - runs entirely with Testcontainers + LocalStack.
 */
@SpringBootTest(classes = ConnectorServiceApplication.class)
@Testcontainers
class AwsSecretsManagerIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("regulyn")
        .withUsername("regulyn_user")
        .withPassword("test-password");
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack:3.0"))
        .withServices(SECRETSMANAGER)
        .withEnv("LOCALSTACK_API_KEY", "test") // For pro features if needed
        .withEnv("DEBUG", "1");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        
        // AWS Secrets Manager - point to LocalStack
        registry.add("aws.secrets.enabled", () -> "true");
        registry.add("aws.secrets.region", () -> localstack.getRegion());
        registry.add("aws.secrets.endpointOverride", () -> localstack.getEndpointOverride(SECRETSMANAGER).toString());
        registry.add("aws.secrets.cacheTtlSeconds", () -> "10");
        registry.add("aws.secrets.failClosed", () -> "true");
    }
    
    @Autowired
    private AwsSecretsManagerCredentialResolver awsResolver;
    
    @Autowired
    private ConnectorCredentialRepository credentialRepository;
    
    @Autowired
    private io.regulyn.connector.repository.ConnectorRepository connectorRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private SecretsManagerClient localstackClient;
    private UUID tenantId;
    private UUID connectorId;
    
    @BeforeEach
    void setUp() {
        // Clean database
        credentialRepository.deleteAll();
        connectorRepository.deleteAll();
        
        // Create LocalStack Secrets Manager client
        localstackClient = SecretsManagerClient.builder()
            .region(Region.of(localstack.getRegion()))
            .endpointOverride(localstack.getEndpointOverride(SECRETSMANAGER))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
            ))
            .build();
        
        tenantId = UUID.randomUUID();
        
        // Create a test connector for foreign key constraint
        io.regulyn.connector.model.Connector connector = new io.regulyn.connector.model.Connector();
        connector.setTenantId(tenantId);
        connector.setConnectorName("Test Connector");
        connector.setConnectorType("WEBHOOK");
        connector.setStatus("ACTIVE");
        connector.setAuthType("API_KEY");
        connector.setMetadata(Map.of());
        connector = connectorRepository.save(connector); // Save and get generated ID
        connectorId = connector.getConnectorId(); // Use the DB-generated ID
    }
    
    @Test
    void testResolveCredentialFromLocalStack() throws Exception {
        // 1. Create a secret in LocalStack
        String secretName = "regulyn-test-secret-" + UUID.randomUUID();
        Map<String, Object> secretData = Map.of(
            "accessToken", "test-access-token-12345",
            "refreshToken", "test-refresh-token-67890",
            "clientId", "test-client-id",
            "clientSecret", "test-client-secret",
            "apiKey", "test-api-key-abcdef"
        );
        String secretJson = objectMapper.writeValueAsString(secretData);
        
        CreateSecretRequest createRequest = CreateSecretRequest.builder()
            .name(secretName)
            .secretString(secretJson)
            .build();
        
        CreateSecretResponse createResponse = localstackClient.createSecret(createRequest);
        assertNotNull(createResponse.arn());
        
        // 2. Insert credential metadata into database pointing to LocalStack secret
        ConnectorCredential credential = new ConnectorCredential();
        credential.setTenantId(tenantId);
        credential.setConnectorId(connectorId);
        credential.setCredentialType("OAUTH2");
        credential.setSecretProvider(ConnectorCredential.SecretProviderType.AWS_SECRETS_MANAGER);
        credential.setSecretId(secretName); // Use secret name for LocalStack
        credentialRepository.save(credential);
        
        // 3. Resolve credential using the resolver
        ResolvedCredential resolved = awsResolver.resolve(tenantId, connectorId, "OAUTH2");
        
        assertNotNull(resolved);
        assertEquals("OAUTH2", resolved.credentialType());
        assertNotNull(resolved.value());
        
        // 4. Verify the resolved value contains the secret data
        Map<String, Object> resolvedData = objectMapper.readValue(resolved.value(), Map.class);
        assertEquals("test-access-token-12345", resolvedData.get("accessToken"));
        assertEquals("test-refresh-token-67890", resolvedData.get("refreshToken"));
        assertEquals("test-client-id", resolvedData.get("clientId"));
        assertEquals("test-client-secret", resolvedData.get("clientSecret"));
        assertEquals("test-api-key-abcdef", resolvedData.get("apiKey"));
        
        // 5. Verify metadata updated in database
        ConnectorCredential updated = credentialRepository.findById(credential.getCredentialId()).orElseThrow();
        assertNotNull(updated.getLastResolvedAt());
        assertEquals(0, updated.getResolveFailCount());
        
        // 6. Verify metadata in resolved credential
        Map<String, String> metadata = resolved.metadata();
        assertEquals("aws_secrets_manager", metadata.get("source"));
        assertEquals(secretName, metadata.get("secret_id"));
        assertEquals("AWSCURRENT", metadata.get("secret_version"));
    }
    
    @Test
    void testCaching() throws Exception {
        // Create secret in LocalStack
        String secretName = "regulyn-cache-test-" + UUID.randomUUID();
        String secretJson = objectMapper.writeValueAsString(Map.of("apiKey", "cached-key-123"));
        
        localstackClient.createSecret(CreateSecretRequest.builder()
            .name(secretName)
            .secretString(secretJson)
            .build());
        
        // Insert credential metadata
        ConnectorCredential credential = new ConnectorCredential();
        credential.setTenantId(tenantId);
        credential.setConnectorId(connectorId);
        credential.setCredentialType("API_KEY");
        credential.setSecretProvider(ConnectorCredential.SecretProviderType.AWS_SECRETS_MANAGER);
        credential.setSecretId(secretName);
        credentialRepository.save(credential);
        
        // First resolution - hits LocalStack
        ResolvedCredential first = awsResolver.resolve(tenantId, connectorId, "API_KEY");
        assertNotNull(first);
        
        // Second resolution - should be cached (within 10 second TTL)
        ResolvedCredential second = awsResolver.resolve(tenantId, connectorId, "API_KEY");
        assertNotNull(second);
        assertEquals(first.value(), second.value());
        
        // Both should have same cache timestamp
        assertThat(second.metadata().get("cached_at")).isNotNull();
    }
    
    @Test
    void testMissingSecretThrowsException() {
        // Insert credential metadata pointing to non-existent secret
        ConnectorCredential credential = new ConnectorCredential();
        credential.setTenantId(tenantId);
        credential.setConnectorId(connectorId);
        credential.setCredentialType("API_KEY");
        credential.setSecretProvider(ConnectorCredential.SecretProviderType.AWS_SECRETS_MANAGER);
        credential.setSecretId("non-existent-secret-12345");
        credentialRepository.save(credential);
        
        // Should throw CredentialResolutionException
        assertThrows(CredentialResolutionException.class, () -> {
            awsResolver.resolve(tenantId, connectorId, "API_KEY");
        });
        
        // Verify fail count incremented
        ConnectorCredential updated = credentialRepository.findById(credential.getCredentialId()).orElseThrow();
        assertEquals(1, updated.getResolveFailCount());
    }
    
    @Test
    void testFailClosedBehavior() {
        // Create credential without secret_id (misconfigured)
        ConnectorCredential credential = new ConnectorCredential();
        credential.setTenantId(tenantId);
        credential.setConnectorId(connectorId);
        credential.setCredentialType("BEARER_TOKEN");
        credential.setSecretProvider(ConnectorCredential.SecretProviderType.AWS_SECRETS_MANAGER);
        credential.setSecretId(null); // No secret ID!
        credentialRepository.save(credential);
        
        // Should throw exception (fail closed)
        assertThrows(CredentialResolutionException.class, () -> {
            awsResolver.resolve(tenantId, connectorId, "BEARER_TOKEN");
        });
    }
    
    @Test
    void testStoreAndResolve() throws Exception {
        // Store a new credential
        String credentialValue = objectMapper.writeValueAsString(Map.of(
            "username", "testuser",
            "password", "testpass123",
            "apiKey", "test-key-xyz"
        ));
        
        awsResolver.store(tenantId, connectorId, "BASIC_AUTH", credentialValue);
        
        // Verify it was created in LocalStack and database
        ConnectorCredential credential = credentialRepository
            .findByTenantIdAndConnectorIdAndCredentialType(tenantId, connectorId, "BASIC_AUTH")
            .orElseThrow();
        
        assertNotNull(credential.getSecretId());
        assertEquals(ConnectorCredential.SecretProviderType.AWS_SECRETS_MANAGER, credential.getSecretProvider());
        
        // Resolve it back
        ResolvedCredential resolved = awsResolver.resolve(tenantId, connectorId, "BASIC_AUTH");
        assertNotNull(resolved);
        
        Map<String, Object> resolvedData = objectMapper.readValue(resolved.value(), Map.class);
        assertEquals("testuser", resolvedData.get("username"));
        assertEquals("testpass123", resolvedData.get("password"));
        assertEquals("test-key-xyz", resolvedData.get("apiKey"));
    }
    
    @Test
    void testExists() throws Exception {
        // Create secret in LocalStack
        String secretName = "regulyn-exists-test-" + UUID.randomUUID();
        localstackClient.createSecret(CreateSecretRequest.builder()
            .name(secretName)
            .secretString("{\"test\": \"data\"}")
            .build());
        
        // Insert credential metadata
        ConnectorCredential credential = new ConnectorCredential();
        credential.setTenantId(tenantId);
        credential.setConnectorId(connectorId);
        credential.setCredentialType("HMAC_SECRET");
        credential.setSecretProvider(ConnectorCredential.SecretProviderType.AWS_SECRETS_MANAGER);
        credential.setSecretId(secretName);
        credentialRepository.save(credential);
        
        // Should return true
        assertTrue(awsResolver.exists(tenantId, connectorId, "HMAC_SECRET"));
        
        // Non-existent credential should return false
        assertFalse(awsResolver.exists(tenantId, connectorId, "DOES_NOT_EXIST"));
    }
    
    @Test
    void testDelete() throws Exception {
        // Create secret in LocalStack
        String secretName = "regulyn-delete-test-" + UUID.randomUUID();
        localstackClient.createSecret(CreateSecretRequest.builder()
            .name(secretName)
            .secretString("{\"test\": \"data\"}")
            .build());
        
        // Insert credential metadata
        ConnectorCredential credential = new ConnectorCredential();
        credential.setTenantId(tenantId);
        credential.setConnectorId(connectorId);
        credential.setCredentialType("OAUTH2");
        credential.setSecretProvider(ConnectorCredential.SecretProviderType.AWS_SECRETS_MANAGER);
        credential.setSecretId(secretName);
        credentialRepository.save(credential);
        UUID credentialId = credential.getCredentialId();
        
        // Delete the credential
        awsResolver.delete(tenantId, connectorId, "OAUTH2");
        
        // Verify metadata removed from database
        assertFalse(credentialRepository.findById(credentialId).isPresent());
        
        // Secret should be scheduled for deletion in LocalStack (with recovery window)
        // We can't easily verify this without accessing LocalStack internals,
        // but we can verify no exception was thrown
    }
    
    @Test
    void testInvalidJsonSecretThrowsException() throws Exception {
        // Create secret with invalid JSON
        String secretName = "regulyn-invalid-json-" + UUID.randomUUID();
        localstackClient.createSecret(CreateSecretRequest.builder()
            .name(secretName)
            .secretString("this is not valid JSON {{{")
            .build());
        
        // Insert credential metadata
        ConnectorCredential credential = new ConnectorCredential();
        credential.setTenantId(tenantId);
        credential.setConnectorId(connectorId);
        credential.setCredentialType("BASIC_AUTH");
        credential.setSecretProvider(ConnectorCredential.SecretProviderType.AWS_SECRETS_MANAGER);
        credential.setSecretId(secretName);
        credentialRepository.save(credential);
        
        // Should throw exception when trying to parse
        assertThrows(CredentialResolutionException.class, () -> {
            awsResolver.resolve(tenantId, connectorId, "BASIC_AUTH");
        });
    }
}
