package io.regulyn.connector.credentials;

/**
 * Strategy for resolving connector credentials from different sources.
 */
public enum CredentialResolverType {
    /**
     * Read from environment variables (for development/testing).
     */
    ENV,
    
    /**
     * Read from local database with encryption at rest (default).
     */
    LOCAL_DB_ENCRYPTED,
    
    /**
     * Read from AWS Secrets Manager (production).
     */
    AWS_SECRETS_MANAGER
}
