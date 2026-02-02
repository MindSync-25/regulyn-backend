package io.regulyn.connector.credentials;

/**
 * Exception thrown when credential cannot be found.
 */
public class CredentialNotFoundException extends RuntimeException {
    public CredentialNotFoundException(String message) {
        super(message);
    }
    
    public CredentialNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
