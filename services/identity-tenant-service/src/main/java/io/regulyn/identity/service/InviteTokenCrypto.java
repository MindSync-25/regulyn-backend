package io.regulyn.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class InviteTokenCrypto {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public InviteTokenCrypto(@Value("${invite.token.encryption-key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("invite.token.encryption-key must be 32 bytes (base64) for AES-256");
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    public EncryptedToken encrypt(String rawToken, String aad) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            if (aad != null) {
                cipher.updateAAD(aad.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            byte[] ciphertext = cipher.doFinal(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            return new EncryptedToken(
                    Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(ciphertext)
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt invite token", ex);
        }
    }

    public String decrypt(String ivBase64, String ciphertextBase64, String aad) {
        try {
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            if (aad != null) {
                cipher.updateAAD(aad.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt invite token", ex);
        }
    }

    public record EncryptedToken(String ivBase64, String ciphertextBase64) {
    }
}
