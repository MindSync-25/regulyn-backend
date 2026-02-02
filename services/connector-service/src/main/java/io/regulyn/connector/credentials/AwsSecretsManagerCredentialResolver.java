package io.regulyn.connector.credentials;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.outbox.OutboxEvent;
import com.regulyn.events.outbox.OutboxEventRepository;
import com.regulyn.events.outbox.OutboxStatus;
import io.regulyn.connector.config.AwsSecretsConfig;
import io.regulyn.connector.model.ConnectorCredential;
import io.regulyn.connector.repository.ConnectorCredentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real AWS Secrets Manager credential resolver implementation.
 * Fetches credentials from AWS Secrets Manager with caching and audit logging.
 */
@Component
@ConditionalOnBean(SecretsManagerClient.class)
public class AwsSecretsManagerCredentialResolver implements CredentialResolver {
    private static final Logger logger = LoggerFactory.getLogger(AwsSecretsManagerCredentialResolver.class);
    
    private final SecretsManagerClient secretsManagerClient;
    private final AwsSecretsConfig config;
    private final ConnectorCredentialRepository credentialRepository;
    private final AuditWriter auditWriter;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    
    // In-memory cache: key = "tenantId:connectorId:secretId:version", value = CachedSecret
    private final Map<String, CachedSecret> cache = new ConcurrentHashMap<>();
    
    public AwsSecretsManagerCredentialResolver(
        SecretsManagerClient secretsManagerClient,
        AwsSecretsConfig config,
        ConnectorCredentialRepository credentialRepository,
        AuditWriter auditWriter,
        OutboxEventRepository outboxRepository,
        ObjectMapper objectMapper
    ) {
        this.secretsManagerClient = secretsManagerClient;
        this.config = config;
        this.credentialRepository = credentialRepository;
        this.auditWriter = auditWriter;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public ResolvedCredential resolve(UUID tenantId, UUID connectorId, String credentialType) {
        // Find credential metadata from database
        ConnectorCredential credential = credentialRepository
            .findByTenantIdAndConnectorIdAndCredentialType(tenantId, connectorId, credentialType)
            .orElseThrow(() -> new CredentialNotFoundException(
                "Credential not found: connectorId=" + connectorId + ", type=" + credentialType
            ));
        
        if (credential.getSecretProvider() != ConnectorCredential.SecretProviderType.AWS_SECRETS_MANAGER) {
            throw new IllegalArgumentException(
                "Credential is not configured for AWS Secrets Manager: " + credential.getSecretProvider()
            );
        }
        
        if (credential.getSecretId() == null || credential.getSecretId().isBlank()) {
            throw new CredentialResolutionException("Secret ID is not configured for credential: " + credential.getCredentialId());
        }
        
        String cacheKey = buildCacheKey(tenantId, connectorId, credential.getSecretId(), credential.getSecretVersion());
        
        // Check cache
        CachedSecret cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            logger.debug("Returning cached secret for connector: {}", connectorId);
            return cached.resolvedCredential;
        }
        
        try {
            // Fetch from AWS Secrets Manager
            String secretValue = fetchSecretValue(credential.getSecretId(), credential.getSecretVersion());
            
            // Parse JSON secret into ConnectorCredentials structure
            JsonNode secretJson = objectMapper.readTree(secretValue);
            
            // Update credential metadata
            credential.setLastResolvedAt(Instant.now());
            credential.resetFailCount();
            credentialRepository.save(credential);
            
            // Create resolved credential
            ResolvedCredential resolved = ResolvedCredential.withMetadata(
                credentialType,
                secretValue, // Keep as raw JSON
                Map.of(
                    "source", "aws_secrets_manager",
                    "secret_id", credential.getSecretId(),
                    "secret_version", credential.getSecretVersion() != null ? credential.getSecretVersion() : "AWSCURRENT",
                    "tenant_id", tenantId.toString(),
                    "cached_at", Instant.now().toString()
                )
            );
            
            // Cache the result
            cache.put(cacheKey, new CachedSecret(resolved, config.getCacheTtl()));
            
            // Audit successful resolution
            auditSuccessfulResolution(tenantId, connectorId, credential);
            
            logger.info("Successfully resolved credential from AWS Secrets Manager: connectorId={}, secretId={}", 
                connectorId, scrubSecretId(credential.getSecretId()));
            
            return resolved;
            
        } catch (Exception e) {
            // Update fail count
            credential.incrementFailCount();
            credentialRepository.save(credential);
            
            // Audit failed resolution
            auditFailedResolution(tenantId, connectorId, credential, e);
            
            logger.error("Failed to resolve credential from AWS Secrets Manager: connectorId={}, secretId={}, failCount={}", 
                connectorId, scrubSecretId(credential.getSecretId()), credential.getResolveFailCount(), e);
            
            throw new CredentialResolutionException(
                "Failed to resolve credential from AWS Secrets Manager: " + scrubError(e.getMessage()), e
            );
        }
    }
    
    @Override
    public void store(UUID tenantId, UUID connectorId, String credentialType, String value) {
        try {
            // Parse secret value to ensure it's valid JSON
            JsonNode secretJson = objectMapper.readTree(value);
            
            // Generate secret name
            String secretName = buildSecretName(tenantId, connectorId, credentialType);
            
            // Check if secret exists
            boolean exists = checkSecretExists(secretName);
            
            if (exists) {
                // Update existing secret
                UpdateSecretRequest updateRequest = UpdateSecretRequest.builder()
                    .secretId(secretName)
                    .secretString(value)
                    .build();
                
                UpdateSecretResponse response = secretsManagerClient.updateSecret(updateRequest);
                logger.info("Updated AWS secret: {}", scrubSecretId(response.arn()));
                
            } else {
                // Create new secret
                CreateSecretRequest createRequest = CreateSecretRequest.builder()
                    .name(secretName)
                    .secretString(value)
                    .tags(
                        Tag.builder().key("tenant_id").value(tenantId.toString()).build(),
                        Tag.builder().key("connector_id").value(connectorId.toString()).build(),
                        Tag.builder().key("credential_type").value(credentialType).build(),
                        Tag.builder().key("managed_by").value("regulyn").build()
                    )
                    .build();
                
                CreateSecretResponse response = secretsManagerClient.createSecret(createRequest);
                logger.info("Created AWS secret: {}", scrubSecretId(response.arn()));
                
                // Save metadata to database
                ConnectorCredential credential = new ConnectorCredential();
                credential.setTenantId(tenantId);
                credential.setConnectorId(connectorId);
                credential.setCredentialType(credentialType);
                credential.setSecretProvider(ConnectorCredential.SecretProviderType.AWS_SECRETS_MANAGER);
                credential.setSecretId(response.arn());
                credentialRepository.save(credential);
            }
            
            // Invalidate cache
            cache.remove(buildCacheKey(tenantId, connectorId, secretName, null));
            
        } catch (Exception e) {
            logger.error("Failed to store secret in AWS Secrets Manager: connectorId={}", connectorId, e);
            throw new CredentialResolutionException("Failed to store secret: " + scrubError(e.getMessage()), e);
        }
    }
    
    @Override
    public void delete(UUID tenantId, UUID connectorId, String credentialType) {
        ConnectorCredential credential = credentialRepository
            .findByTenantIdAndConnectorIdAndCredentialType(tenantId, connectorId, credentialType)
            .orElseThrow(() -> new CredentialNotFoundException(
                "Credential not found for deletion: connectorId=" + connectorId
            ));
        
        if (credential.getSecretId() == null) {
            logger.warn("No secret ID configured for credential: {}", credential.getCredentialId());
            return;
        }
        
        try {
            // Schedule deletion with recovery window
            DeleteSecretRequest deleteRequest = DeleteSecretRequest.builder()
                .secretId(credential.getSecretId())
                .recoveryWindowInDays(7L) // 7-day recovery window
                .build();
            
            secretsManagerClient.deleteSecret(deleteRequest);
            
            // Invalidate cache
            cache.remove(buildCacheKey(tenantId, connectorId, credential.getSecretId(), credential.getSecretVersion()));
            
            // Delete metadata from database
            credentialRepository.delete(credential);
            
            logger.info("Scheduled deletion of AWS secret (7-day recovery): {}", scrubSecretId(credential.getSecretId()));
            
        } catch (ResourceNotFoundException e) {
            logger.warn("Secret not found in AWS, deleting local metadata: {}", scrubSecretId(credential.getSecretId()));
            credentialRepository.delete(credential);
        } catch (Exception e) {
            logger.error("Failed to delete secret from AWS Secrets Manager: {}", scrubSecretId(credential.getSecretId()), e);
            throw new CredentialResolutionException("Failed to delete secret: " + scrubError(e.getMessage()), e);
        }
    }
    
    @Override
    public boolean exists(UUID tenantId, UUID connectorId, String credentialType) {
        return credentialRepository
            .findByTenantIdAndConnectorIdAndCredentialType(tenantId, connectorId, credentialType)
            .filter(c -> c.getSecretProvider() == ConnectorCredential.SecretProviderType.AWS_SECRETS_MANAGER)
            .filter(c -> c.getSecretId() != null)
            .map(c -> checkSecretExists(c.getSecretId()))
            .orElse(false);
    }
    
    private String fetchSecretValue(String secretId, String versionId) {
        GetSecretValueRequest.Builder requestBuilder = GetSecretValueRequest.builder()
            .secretId(secretId);
        
        if (versionId != null && !versionId.isBlank()) {
            requestBuilder.versionId(versionId);
        }
        
        GetSecretValueResponse response = secretsManagerClient.getSecretValue(requestBuilder.build());
        
        if (response.secretString() == null) {
            throw new CredentialResolutionException("Secret value is binary, expected JSON string: " + scrubSecretId(secretId));
        }
        
        return response.secretString();
    }
    
    private boolean checkSecretExists(String secretId) {
        try {
            DescribeSecretRequest request = DescribeSecretRequest.builder()
                .secretId(secretId)
                .build();
            
            secretsManagerClient.describeSecret(request);
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }
    
    private String buildSecretName(UUID tenantId, UUID connectorId, String credentialType) {
        return String.format("regulyn/connector/%s/%s/%s", tenantId, connectorId, credentialType);
    }
    
    private String buildCacheKey(UUID tenantId, UUID connectorId, String secretId, String version) {
        return String.format("%s:%s:%s:%s", tenantId, connectorId, secretId, version != null ? version : "CURRENT");
    }
    
    private String scrubSecretId(String secretId) {
        if (secretId == null) return "null";
        // Only show last 8 characters
        if (secretId.length() > 20) {
            return "***" + secretId.substring(secretId.length() - 8);
        }
        return "***";
    }
    
    private String scrubError(String message) {
        if (message == null) return "Unknown error";
        // Remove potential secret values from error messages
        return message.replaceAll("(?i)(secret|password|token|key)[\"']?\\s*[:=]\\s*[\"']?[^\"'\\s]+", "$1=***");
    }
    
    private void auditSuccessfulResolution(UUID tenantId, UUID connectorId, ConnectorCredential credential) {
        try {
            auditWriter.auditAction(
                "CREDENTIAL_RESOLUTION_SUCCEEDED",
                "CONNECTOR_CREDENTIAL",
                credential.getCredentialId().toString(),
                "N/A",
                null,
                objectMapper.createObjectNode()
                    .put("connector_id", connectorId.toString())
                    .put("credential_type", credential.getCredentialType())
                    .put("provider", "AWS_SECRETS_MANAGER")
                    .put("secret_id", scrubSecretId(credential.getSecretId()))
            );
            
            // Emit outbox event
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setOutboxId(UUID.randomUUID());
            outboxEvent.setTenantId(tenantId);
            outboxEvent.setEventId(UUID.randomUUID());
            outboxEvent.setEventType("connector.credential.resolution.succeeded");
            outboxEvent.setSourceService("connector-service");
            outboxEvent.setEntityType("connector_credential");
            outboxEvent.setEntityId(credential.getCredentialId().toString());
            outboxEvent.setPayload("{\"connector_id\":\"" + connectorId + "\",\"provider\":\"AWS_SECRETS_MANAGER\"}");
            outboxEvent.setPayloadHash("N/A");
            outboxEvent.setCorrelationId(connectorId.toString()); // Use connectorId as correlation
            outboxEvent.setStatus(OutboxStatus.PENDING);
            outboxEvent.setOccurredAt(Instant.now());
            outboxEvent.setNextAttemptAt(Instant.now());
            outboxRepository.save(outboxEvent);
            
        } catch (Exception e) {
            logger.warn("Failed to audit successful credential resolution", e);
        }
    }
    
    private void auditFailedResolution(UUID tenantId, UUID connectorId, ConnectorCredential credential, Exception error) {
        try {
            auditWriter.auditAction(
                "CREDENTIAL_RESOLUTION_FAILED",
                "CONNECTOR_CREDENTIAL",
                credential.getCredentialId().toString(),
                "N/A",
                null,
                objectMapper.createObjectNode()
                    .put("connector_id", connectorId.toString())
                    .put("credential_type", credential.getCredentialType())
                    .put("provider", "AWS_SECRETS_MANAGER")
                    .put("secret_id", scrubSecretId(credential.getSecretId()))
                    .put("error", scrubError(error.getMessage()))
                    .put("fail_count", credential.getResolveFailCount())
            );
            
            // Emit outbox event
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setOutboxId(UUID.randomUUID());
            outboxEvent.setTenantId(tenantId);
            outboxEvent.setEventId(UUID.randomUUID());
            outboxEvent.setEventType("connector.credential.resolution.failed");
            outboxEvent.setSourceService("connector-service");
            outboxEvent.setEntityType("connector_credential");
            outboxEvent.setEntityId(credential.getCredentialId().toString());
            outboxEvent.setPayload("{\"connector_id\":\"" + connectorId + "\",\"provider\":\"AWS_SECRETS_MANAGER\",\"error\":\"" + scrubError(error.getMessage()) + "\"}");
            outboxEvent.setPayloadHash("N/A");
            outboxEvent.setCorrelationId(connectorId.toString()); // Use connectorId as correlation
            outboxEvent.setStatus(OutboxStatus.PENDING);
            outboxEvent.setOccurredAt(Instant.now());
            outboxEvent.setNextAttemptAt(Instant.now());
            outboxRepository.save(outboxEvent);
            
        } catch (Exception e) {
            logger.warn("Failed to audit failed credential resolution", e);
        }
    }
    
    /**
     * Cached secret with expiration
     */
    private static class CachedSecret {
        final ResolvedCredential resolvedCredential;
        final Instant expiresAt;
        
        CachedSecret(ResolvedCredential resolvedCredential, java.time.Duration ttl) {
            this.resolvedCredential = resolvedCredential;
            this.expiresAt = Instant.now().plus(ttl);
        }
        
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
