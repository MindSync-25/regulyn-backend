package io.regulyn.connector.credentials;

import io.regulyn.connector.config.CredentialConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption service for credential storage.
 * Uses 96-bit IV (12 bytes) and 128-bit authentication tag.
 */
@Service
public class EncryptionService {

    private static final Logger logger = LoggerFactory.getLogger(EncryptionService.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits

    private final CredentialConfig credentialConfig;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionService(CredentialConfig credentialConfig) {
        this.credentialConfig = credentialConfig;
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM.
     *
     * @param plaintext The data to encrypt
     * @return EncryptedData containing base64-encoded ciphertext and IV
     */
    public EncryptedData encrypt(byte[] plaintext) {
        try {
            byte[] keyBytes = credentialConfig.getEncryption().getKeyBytes();
            SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");

            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext);

            return new EncryptedData(
                Base64.getEncoder().encodeToString(ciphertext),
                Base64.getEncoder().encodeToString(iv)
            );
        } catch (Exception e) {
            logger.error("Encryption failed", e);
            throw new EncryptionException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypts ciphertext using AES-256-GCM.
     *
     * @param encPayloadBase64 Base64-encoded ciphertext
     * @param ivBase64         Base64-encoded IV
     * @return Decrypted plaintext bytes
     */
    public byte[] decrypt(String encPayloadBase64, String ivBase64) {
        try {
            byte[] keyBytes = credentialConfig.getEncryption().getKeyBytes();
            SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");

            byte[] ciphertext = Base64.getDecoder().decode(encPayloadBase64);
            byte[] iv = Base64.getDecoder().decode(ivBase64);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            logger.error("Decryption failed", e);
            throw new EncryptionException("Failed to decrypt data", e);
        }
    }

    /**
     * Generates a new random 32-byte AES-256 key encoded as base64.
     * Useful for initial setup and testing.
     *
     * @return Base64-encoded 256-bit key
     */
    public static String generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey key = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }

    /**
     * Container for encrypted data.
     */
    public static class EncryptedData {
        private final String encPayload;
        private final String iv;

        public EncryptedData(String encPayload, String iv) {
            this.encPayload = encPayload;
            this.iv = iv;
        }

        public String getEncPayload() {
            return encPayload;
        }

        public String getIv() {
            return iv;
        }
    }

    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
