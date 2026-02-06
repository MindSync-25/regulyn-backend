package io.regulyn.connector.credentials;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.events.outbox.OutboxEvent;
import com.regulyn.events.outbox.OutboxEventRepository;
import io.regulyn.connector.model.ConnectorCredential;
import io.regulyn.connector.repository.ConnectorCredentialRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for resolving connector credentials from multiple providers.
 * Supports: ENV, LOCAL_DB_ENCRYPTED, AWS_SECRETS_MANAGER.
 * Emits audit events and outbox events for all resolution attempts.
 */
@Service
public class CredentialResolutionService {

    private static final Logger logger = LoggerFactory.getLogger(CredentialResolutionService.class);

    private final ConnectorCredentialRepository credentialRepository;
    private final EncryptionService encryptionService;
    private final Optional<SecretsManagerClient> secretsManagerClient;
    private final OutboxEventRepository outboxRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    // In-memory cache: key = tenantId:connectorId:secretId:version, value = CacheEntry
    private final ConcurrentHashMap<String, CacheEntry> secretCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    public CredentialResolutionService(
            ConnectorCredentialRepository credentialRepository,
            EncryptionService encryptionService,
            Optional<SecretsManagerClient> secretsManagerClient,
            OutboxEventRepository outboxRepository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.credentialRepository = credentialRepository;
        this.encryptionService = encryptionService;
        this.secretsManagerClient = secretsManagerClient;
        this.outboxRepository = outboxRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolves credentials for a given connector.
     *
     * @param tenantId      Tenant ID
     * @param connectorId   Connector ID
     * @param correlationId Correlation ID for tracking
     * @return Resolved credentials
     * @throws CredentialResolutionException if resolution fails
     */
    public ResolvedCredentials resolve(UUID tenantId, UUID connectorId, String correlationId) {
        Instant startTime = Instant.now();
        String safeCorrelationId = normalizeCorrelationId(correlationId, "cred-");
        emitOutboxEvent(tenantId, connectorId, safeCorrelationId, "CREDENTIAL_RESOLVE_STARTED", null, null);
        emitAuditEvent(tenantId, connectorId, safeCorrelationId, "CREDENTIAL_RESOLVE_STARTED", null, null);

        try {
            // Load credential row
            ConnectorCredential credential = credentialRepository
                    .findByTenantIdAndConnectorId(tenantId, connectorId)
                    .orElseThrow(() -> new CredentialResolutionException(
                            "CREDENTIAL_NOT_FOUND",
                            "No credential configuration found for connector: " + connectorId
                    ));

            String provider = credential.getProvider().name();
            ResolvedCredentials resolved;

            switch (credential.getProvider()) {
                case ENV:
                    resolved = resolveFromEnv(credential);
                    break;
                case LOCAL_DB_ENCRYPTED:
                    resolved = resolveFromLocalDb(credential);
                    break;
                case AWS_SECRETS_MANAGER:
                    resolved = resolveFromAwsSecretsManager(credential);
                    break;
                default:
                    throw new CredentialResolutionException(
                            "UNSUPPORTED_PROVIDER",
                            "Unsupported credential provider: " + credential.getProvider()
                    );
            }

            emitOutboxEvent(tenantId, connectorId, safeCorrelationId, "CREDENTIAL_RESOLVE_SUCCEEDED", provider, null);
            emitAuditEvent(tenantId, connectorId, safeCorrelationId, "CREDENTIAL_RESOLVE_SUCCEEDED", provider, null);

            logger.info("Successfully resolved credentials for connector {} using provider {}", connectorId, provider);
            return resolved;

        } catch (CredentialResolutionException e) {
            emitOutboxEvent(tenantId, connectorId, safeCorrelationId, "CREDENTIAL_RESOLVE_FAILED", null, e.getErrorCode());
            emitAuditEvent(tenantId, connectorId, safeCorrelationId, "CREDENTIAL_RESOLVE_FAILED", null, e.getErrorCode());
            logger.error("Credential resolution failed for connector {}: {}", connectorId, e.getMessage());
            throw e;
        } catch (Exception e) {
            emitOutboxEvent(tenantId, connectorId, safeCorrelationId, "CREDENTIAL_RESOLVE_FAILED", null, "INTERNAL_ERROR");
            emitAuditEvent(tenantId, connectorId, safeCorrelationId, "CREDENTIAL_RESOLVE_FAILED", null, "INTERNAL_ERROR");
            logger.error("Unexpected error resolving credentials for connector {}", connectorId, e);
            throw new CredentialResolutionException("INTERNAL_ERROR", "Unexpected error during credential resolution", e);
        }
    }

    private ResolvedCredentials resolveFromEnv(ConnectorCredential credential) {
        try {
            // Decrypt payload to get env var mapping (JSON: {"client_id": "ENV_VAR_NAME_1", ...})
            String encPayloadBase64 = java.util.Base64.getEncoder().encodeToString(credential.getEncPayload());
            String ivBase64 = java.util.Base64.getEncoder().encodeToString(credential.getEncIv());
            byte[] decryptedBytes = encryptionService.decrypt(encPayloadBase64, ivBase64);
            String decryptedJson = new String(decryptedBytes, java.nio.charset.StandardCharsets.UTF_8);
            Map<String, String> envVarMapping = objectMapper.readValue(decryptedJson, new TypeReference<Map<String, String>>() {});

            Map<String, String> resolvedCreds = new HashMap<>();
            for (Map.Entry<String, String> entry : envVarMapping.entrySet()) {
                String credentialKey = entry.getKey();
                String envVarName = entry.getValue();
                String envValue = System.getenv(envVarName);

                if (envValue == null || envValue.isEmpty()) {
                    throw new CredentialResolutionException(
                            "ENV_VAR_NOT_FOUND",
                            "Environment variable not found or empty: " + envVarName
                    );
                }
                resolvedCreds.put(credentialKey, envValue);
            }

            return new ResolvedCredentials(resolvedCreds, "ENV");

        } catch (CredentialResolutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CredentialResolutionException("ENV_RESOLUTION_FAILED", "Failed to resolve credentials from environment", e);
        }
    }

    private ResolvedCredentials resolveFromLocalDb(ConnectorCredential credential) {
        try {
            // Decrypt payload directly (JSON: {"client_id": "value1", "client_secret": "value2", ...})
            String encPayloadBase64 = java.util.Base64.getEncoder().encodeToString(credential.getEncPayload());
            String ivBase64 = java.util.Base64.getEncoder().encodeToString(credential.getEncIv());
            byte[] decryptedBytes = encryptionService.decrypt(encPayloadBase64, ivBase64);
            String decryptedJson = new String(decryptedBytes, java.nio.charset.StandardCharsets.UTF_8);
            Map<String, String> credentials = objectMapper.readValue(decryptedJson, new TypeReference<Map<String, String>>() {});

            return new ResolvedCredentials(credentials, "LOCAL_DB_ENCRYPTED");

        } catch (Exception e) {
            throw new CredentialResolutionException("DECRYPTION_FAILED", "Failed to decrypt local database credentials", e);
        }
    }

    private ResolvedCredentials resolveFromAwsSecretsManager(ConnectorCredential credential) {
        if (secretsManagerClient.isEmpty()) {
            throw new CredentialResolutionException(
                    "AWS_DISABLED",
                    "AWS Secrets Manager is not enabled. Set connector.credentials.aws.enabled=true"
            );
        }

        try {
            // Decrypt payload to get secret metadata (JSON: {"secretId": "my-secret", "versionId": "optional"})
            String encPayloadBase64 = java.util.Base64.getEncoder().encodeToString(credential.getEncPayload());
            String ivBase64 = java.util.Base64.getEncoder().encodeToString(credential.getEncIv());
            byte[] decryptedBytes = encryptionService.decrypt(encPayloadBase64, ivBase64);
            String decryptedJson = new String(decryptedBytes, java.nio.charset.StandardCharsets.UTF_8);
            Map<String, String> secretMetadata = objectMapper.readValue(decryptedJson, new TypeReference<Map<String, String>>() {});

            String secretId = secretMetadata.get("secretId");
            if (secretId == null || secretId.isEmpty()) {
                throw new CredentialResolutionException("INVALID_SECRET_METADATA", "secretId not found in credential payload");
            }

            String versionId = secretMetadata.get("versionId");
            String cacheKey = buildCacheKey(credential.getTenantId(), credential.getConnectorId(), secretId, versionId);

            // Check cache
            CacheEntry cachedEntry = secretCache.get(cacheKey);
            if (cachedEntry != null && !cachedEntry.isExpired()) {
                logger.debug("Cache hit for secret: {}", secretId);
                return new ResolvedCredentials(cachedEntry.credentials, "AWS_SECRETS_MANAGER");
            }

            // Fetch from AWS
            logger.debug("Cache miss for secret: {}, fetching from AWS", secretId);
            GetSecretValueRequest.Builder requestBuilder = GetSecretValueRequest.builder().secretId(secretId);
            if (versionId != null && !versionId.isEmpty()) {
                requestBuilder.versionId(versionId);
            }

            GetSecretValueResponse response = secretsManagerClient.get().getSecretValue(requestBuilder.build());
            String secretString = response.secretString();

            if (secretString == null || secretString.isEmpty()) {
                throw new CredentialResolutionException("EMPTY_SECRET", "AWS secret is empty: " + secretId);
            }

            Map<String, String> credentials = objectMapper.readValue(secretString, new TypeReference<Map<String, String>>() {});

            // Cache result
            secretCache.put(cacheKey, new CacheEntry(credentials, System.currentTimeMillis()));

            return new ResolvedCredentials(credentials, "AWS_SECRETS_MANAGER");

        } catch (CredentialResolutionException e) {
            throw e;
        } catch (Exception e) {
            throw new CredentialResolutionException("AWS_FETCH_FAILED", "Failed to fetch secret from AWS Secrets Manager", e);
        }
    }

    private String buildCacheKey(UUID tenantId, UUID connectorId, String secretId, String versionId) {
        return tenantId + ":" + connectorId + ":" + secretId + ":" + (versionId != null ? versionId : "LATEST");
    }

    public void emitOutboxEvent(UUID tenantId, UUID connectorId, String correlationId, String eventType, String provider, String errorCode) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setTenantId(tenantId);
        outboxEvent.setEventId(UUID.randomUUID());
        outboxEvent.setEventType(eventType);
        outboxEvent.setSourceService("connector-service");
        outboxEvent.setEntityType("CONNECTOR_CREDENTIAL");
        outboxEvent.setEntityId(connectorId.toString());
        outboxEvent.setOccurredAt(Instant.now());
        outboxEvent.setCorrelationId(correlationId);
        outboxEvent.setStatus(com.regulyn.events.outbox.OutboxStatus.PENDING);
        outboxEvent.setNextAttemptAt(Instant.now());

        try {
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("connectorId", connectorId.toString());
            payloadMap.put("correlationId", correlationId);
            if (provider != null) {
                payloadMap.put("provider", provider);
            }
            if (errorCode != null) {
                payloadMap.put("errorCode", errorCode);
            }

            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            outboxEvent.setPayload(payloadJson);
            outboxEvent.setPayloadHash(Integer.toString(payloadJson.hashCode()));
        } catch (Exception e) {
            logger.error("Failed to serialize outbox payload", e);
        }

        outboxRepository.saveAndFlush(outboxEvent);
    }

    private void emitAuditEvent(UUID tenantId, UUID connectorId, String correlationId, String action, String provider, String errorCode) {
        try {
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("connectorId", connectorId.toString());
            payloadMap.put("correlationId", correlationId);
            if (provider != null) {
                payloadMap.put("provider", provider);
            }
            if (errorCode != null) {
                payloadMap.put("errorCode", errorCode);
            }

            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            String sql = """
                INSERT INTO connector.audit_events (
                    event_id, tenant_id, occurred_at, actor_id, actor_type, service,
                    action, entity_type, entity_id, payload_hash, evidence_id, metadata
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """;

            jdbcTemplate.update(
                    sql,
                    UUID.randomUUID(),
                    tenantId,
                    java.sql.Timestamp.from(Instant.now()),
                    null,
                    "SYSTEM",
                    "connector-service",
                    action,
                    "CONNECTOR_CREDENTIAL",
                    connectorId.toString(),
                    Integer.toString(payloadJson.hashCode()),
                    null,
                    payloadJson
            );
        } catch (Exception e) {
            logger.error("Failed to write audit event {} for connector {}", action, connectorId, e);
        }
    }

    private String normalizeCorrelationId(String correlationId, String prefix) {
        String value = correlationId;
        if (value == null || value.isBlank()) {
            value = prefix + UUID.randomUUID();
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    /**
     * Cache entry for AWS secrets.
     */
    private static class CacheEntry {
        final Map<String, String> credentials;
        final long timestamp;

        CacheEntry(Map<String, String> credentials, long timestamp) {
            this.credentials = credentials;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    /**
     * Exception thrown during credential resolution.
     */
    public static class CredentialResolutionException extends RuntimeException {
        private final String errorCode;

        public CredentialResolutionException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public CredentialResolutionException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
