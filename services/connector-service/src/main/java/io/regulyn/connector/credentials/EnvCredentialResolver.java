package io.regulyn.connector.credentials;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Environment variable-based credential resolver (for development/testing).
 * Format: CONNECTOR_{CONNECTOR_ID}_{CREDENTIAL_TYPE}=value
 */
@Component
public class EnvCredentialResolver implements CredentialResolver {
    private static final Logger logger = LoggerFactory.getLogger(EnvCredentialResolver.class);
    
    @Override
    public ResolvedCredential resolve(UUID tenantId, UUID connectorId, String credentialType) {
        String envKey = buildEnvKey(connectorId, credentialType);
        String value = System.getenv(envKey);
        
        if (value == null || value.isBlank()) {
            throw new CredentialNotFoundException(
                "Credential not found in environment for connector: " + connectorId + ", type: " + credentialType
            );
        }
        
        logger.debug("Resolved credential from environment for connector: {}, type: {}", connectorId, credentialType);
        return ResolvedCredential.withMetadata(
            credentialType,
            value,
            Map.of("source", "env", "env_key", envKey)
        );
    }
    
    @Override
    public void store(UUID tenantId, UUID connectorId, String credentialType, String value) {
        throw new UnsupportedOperationException("Cannot store credentials in environment variables");
    }
    
    @Override
    public void delete(UUID tenantId, UUID connectorId, String credentialType) {
        throw new UnsupportedOperationException("Cannot delete credentials from environment variables");
    }
    
    @Override
    public boolean exists(UUID tenantId, UUID connectorId, String credentialType) {
        String envKey = buildEnvKey(connectorId, credentialType);
        String value = System.getenv(envKey);
        return value != null && !value.isBlank();
    }
    
    private String buildEnvKey(UUID connectorId, String credentialType) {
        String normalizedConnectorId = connectorId.toString().replace("-", "_").toUpperCase();
        return "CONNECTOR_" + normalizedConnectorId + "_" + credentialType.toUpperCase();
    }
}
