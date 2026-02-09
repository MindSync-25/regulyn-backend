package io.regulyn.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class ApiKeyTokenService {

    private static final String HMAC_ALG = "HmacSHA256";
    private static final String SHA_256 = "SHA-256";
    private static final int RAW_KEY_BYTES = 32;

    private final byte[] hmacSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyTokenService(@Value("${api.key.hmac-secret}") String hmacSecret) {
        this.hmacSecret = hmacSecret.getBytes(StandardCharsets.UTF_8);
    }

    public String generateRawKey() {
        byte[] raw = new byte[RAW_KEY_BYTES];
        secureRandom.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public String hashHmac(String rawKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(hmacSecret, HMAC_ALG));
            byte[] digest = mac.doFinal(rawKey.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash API key", ex);
        }
    }

    public String hashSha256(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash API key", ex);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
