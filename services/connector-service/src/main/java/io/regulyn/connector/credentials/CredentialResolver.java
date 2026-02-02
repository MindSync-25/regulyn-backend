package io.regulyn.connector.credentials;

import java.util.UUID;

/**
 * Interface for resolving connector credentials from various sources.
 * Implementations must ensure credentials are never logged and are encrypted at rest when stored locally.
 */
public interface CredentialResolver {
    
    /**
     * Resolve credential for a connector.
     * 
     * @param tenantId Tenant identifier
     * @param connectorId Connector identifier
     * @param credentialType Type of credential (API_KEY, OAUTH2, etc.)
     * @return Resolved credential
     * @throws CredentialNotFoundException if credential not found
     * @throws CredentialResolutionException if resolution fails
     */
    ResolvedCredential resolve(UUID tenantId, UUID connectorId, String credentialType);
    
    /**
     * Store a new credential.
     * 
     * @param tenantId Tenant identifier
     * @param connectorId Connector identifier
     * @param credentialType Type of credential
     * @param value Credential value (will be encrypted)
     * @throws CredentialResolutionException if storage fails
     */
    void store(UUID tenantId, UUID connectorId, String credentialType, String value);
    
    /**
     * Delete a credential.
     * 
     * @param tenantId Tenant identifier
     * @param connectorId Connector identifier
     * @param credentialType Type of credential
     * @throws CredentialResolutionException if deletion fails
     */
    void delete(UUID tenantId, UUID connectorId, String credentialType);
    
    /**
     * Check if credential exists.
     * 
     * @param tenantId Tenant identifier
     * @param connectorId Connector identifier
     * @param credentialType Type of credential
     * @return true if credential exists
     */
    boolean exists(UUID tenantId, UUID connectorId, String credentialType);
}
