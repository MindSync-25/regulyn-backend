package io.regulyn.connector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

/**
 * Configuration properties for credential management.
 */
@Configuration
@ConfigurationProperties(prefix = "connector.credentials")
public class CredentialConfig {

    private Encryption encryption = new Encryption();
    private Aws aws = new Aws();

    public Encryption getEncryption() {
        return encryption;
    }

    public void setEncryption(Encryption encryption) {
        this.encryption = encryption;
    }

    public Aws getAws() {
        return aws;
    }

    public void setAws(Aws aws) {
        this.aws = aws;
    }

    public static class Encryption {
        /**
         * Base64-encoded 32-byte encryption key for AES-256-GCM.
         */
        private String key;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public byte[] getKeyBytes() {
            if (key == null || key.isEmpty()) {
                throw new IllegalStateException("Encryption key not configured: connector.credentials.encryption.key");
            }
            try {
                byte[] keyBytes = Base64.getDecoder().decode(key);
                if (keyBytes.length != 32) {
                    throw new IllegalStateException("Encryption key must be 32 bytes (256 bits) for AES-256-GCM");
                }
                return keyBytes;
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid base64 encoding for encryption key", e);
            }
        }
    }

    public static class Aws {
        /**
         * Whether AWS Secrets Manager integration is enabled.
         */
        private boolean enabled = false;

        /**
         * AWS region for Secrets Manager.
         */
        private String region = "us-east-1";

        /**
         * Optional endpoint override for LocalStack testing.
         */
        private String secretsEndpoint;

        /**
         * AWS access key (for LocalStack tests only - do not use in production).
         */
        private String accessKey;

        /**
         * AWS secret key (for LocalStack tests only - do not use in production).
         */
        private String secretKey;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getSecretsEndpoint() {
            return secretsEndpoint;
        }

        public void setSecretsEndpoint(String secretsEndpoint) {
            this.secretsEndpoint = secretsEndpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }
}
