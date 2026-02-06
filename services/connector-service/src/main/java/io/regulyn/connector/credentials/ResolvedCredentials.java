package io.regulyn.connector.credentials;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Resolved credentials returned after provider-specific resolution.
 * Contains key-value pairs (e.g., client_id, client_secret, api_key, etc.).
 */
public class ResolvedCredentials {

    private final Map<String, String> credentials;
    private final String provider;

    public ResolvedCredentials(Map<String, String> credentials, String provider) {
        this.credentials = Collections.unmodifiableMap(credentials);
        this.provider = provider;
    }

    public Map<String, String> getCredentials() {
        return credentials;
    }

    public String getProvider() {
        return provider;
    }

    /**
     * Get a specific credential value by key.
     *
     * @param key Credential key (e.g., "client_id", "api_key")
     * @return Credential value or null if not present
     */
    public String get(String key) {
        return credentials.get(key);
    }

    /**
     * Check if a credential key exists.
     */
    public boolean has(String key) {
        return credentials.containsKey(key);
    }

    /**
     * Get all credential keys present.
     */
    public java.util.Set<String> getKeys() {
        return credentials.keySet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResolvedCredentials that = (ResolvedCredentials) o;
        return Objects.equals(credentials, that.credentials) && Objects.equals(provider, that.provider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(credentials, provider);
    }

    @Override
    public String toString() {
        // Do NOT print actual credential values in toString for security
        return "ResolvedCredentials{" +
                "provider='" + provider + '\'' +
                ", keys=" + credentials.keySet() +
                '}';
    }
}
