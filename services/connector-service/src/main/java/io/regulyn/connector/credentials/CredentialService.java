package io.regulyn.connector.credentials;

import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Credential service that delegates to appropriate resolver based on configuration.
 * Emits audit events for credential access.
 */
@Service
public class CredentialService {
    private static final Logger logger = LoggerFactory.getLogger(CredentialService.class);
    
    private final CredentialResolver activeResolver;
    private final AuditWriter auditWriter;
    private final CredentialResolverType resolverType;
    
    public CredentialService(
        @Value("${connector.credentials.resolver:LOCAL_DB_ENCRYPTED}") String resolverTypeStr,
        EnvCredentialResolver envResolver,
        LocalDbCredentialResolver localDbResolver,
        @Autowired(required = false) AwsSecretsManagerCredentialResolver awsResolver,
        AuditWriter auditWriter
    ) {
        this.resolverType = CredentialResolverType.valueOf(resolverTypeStr);
        this.activeResolver = switch (resolverType) {
            case ENV -> envResolver;
            case LOCAL_DB_ENCRYPTED -> localDbResolver;
            case AWS_SECRETS_MANAGER -> {
                if (awsResolver == null) {
                    throw new IllegalStateException("AWS Secrets Manager resolver is not available. Ensure aws.secrets.enabled=true and required dependencies are configured.");
                }
                yield awsResolver;
            }
        };
        this.auditWriter = auditWriter;
        
        logger.info("Initialized CredentialService with resolver type: {}", resolverType);
    }
    
    /**
     * Resolve credential for a connector.
     * Emits CREDENTIALS_ACCESSED audit event.
     */
    public ResolvedCredential resolveCredential(UUID tenantId, UUID connectorId, String credentialType) {
        try {
            ResolvedCredential credential = activeResolver.resolve(tenantId, connectorId, credentialType);
            
            // Audit credential access (without logging the actual value)
            auditWriter.auditAction(
                "CREDENTIALS_ACCESSED",
                "CONNECTOR",
                connectorId.toString(),
                "N/A",
                null,
                null
            );
            
            return credential;
        } catch (CredentialNotFoundException e) {
            logger.error("Credential not found for connector: {}, type: {}", connectorId, credentialType);
            throw e;
        } catch (Exception e) {
            logger.error("Failed to resolve credential for connector: {}, type: {}", connectorId, credentialType, e);
            throw new CredentialResolutionException("Failed to resolve credential", e);
        }
    }
    
    /**
     * Store a new credential.
     */
    public void storeCredential(UUID tenantId, UUID connectorId, String credentialType, String value) {
        activeResolver.store(tenantId, connectorId, credentialType, value);
        
        auditWriter.auditAction(
            "CREDENTIALS_STORED",
            "CONNECTOR",
            connectorId.toString(),
            "N/A",
            null,
            null
        );
    }
    
    /**
     * Delete a credential.
     */
    public void deleteCredential(UUID tenantId, UUID connectorId, String credentialType) {
        activeResolver.delete(tenantId, connectorId, credentialType);
        
        auditWriter.auditAction(
            "CREDENTIALS_DELETED",
            "CONNECTOR",
            connectorId.toString(),
            "N/A",
            null,
            null
        );
    }
    
    /**
     * Check if credential exists.
     */
    public boolean credentialExists(UUID tenantId, UUID connectorId, String credentialType) {
        return activeResolver.exists(tenantId, connectorId, credentialType);
    }
    
    public CredentialResolverType getResolverType() {
        return resolverType;
    }
}
