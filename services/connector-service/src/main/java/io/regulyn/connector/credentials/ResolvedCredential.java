package io.regulyn.connector.credentials;

import java.util.Map;

/**
 * Resolved credential with metadata.
 */
public record ResolvedCredential(
    String credentialType,
    String value,
    Map<String, String> metadata
) {
    public ResolvedCredential {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Credential value cannot be null or blank");
        }
    }
    
    public static ResolvedCredential of(String type, String value) {
        return new ResolvedCredential(type, value, Map.of());
    }
    
    public static ResolvedCredential withMetadata(String type, String value, Map<String, String> metadata) {
        return new ResolvedCredential(type, value, metadata);
    }
}
