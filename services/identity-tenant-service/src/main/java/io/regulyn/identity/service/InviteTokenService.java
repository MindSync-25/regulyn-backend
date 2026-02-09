package io.regulyn.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class InviteTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final SecureRandom secureRandom = new SecureRandom();

    public InviteTokenService(@Value("${invite.token.secret}") String secret) {
        this.secret = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String rawToken) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute token hash", ex);
        }
    }
}
