package io.regulyn.connector.credentials;

/**
 * Exception thrown when credential resolution fails.
 */
public class CredentialResolutionException extends RuntimeException {
    public CredentialResolutionException(String message) {
        super(message);
    }
    
    public CredentialResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
