package com.regulyn.auth.client;

/**
 * Client interface for authenticating with identity-tenant-service.
 * Implementations should call internal HTTP endpoints to validate credentials.
 */
public interface IdentityAuthClient {
    
    /**
     * Validate a raw API key by calling identity-tenant-service internal endpoint.
     * 
     * @param rawApiKey The plain-text API key to validate
     * @return ValidationResult with tenant/user info if valid, or invalid result
     */
    ValidationResult validateApiKey(String rawApiKey);
}
