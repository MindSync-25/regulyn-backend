package io.regulyn.connector.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Webhook signature verifier using HMAC-SHA256.
 */
public class WebhookSignatureVerifier {
    
    private static final String HMAC_SHA256 = "HmacSHA256";
    
    /**
     * Verify HMAC-SHA256 signature.
     * 
     * @param payload Request body
     * @param secret Webhook secret
     * @param signature Signature from header
     * @param prefix Signature prefix (e.g., "sha256=")
     * @return true if signature is valid
     */
    public static boolean verifyHmacSha256(
        String payload, 
        String secret, 
        String signature,
        String prefix
    ) {
        try {
            String expectedSignature = calculateHmacSha256(payload, secret);
            
            // Remove prefix if present
            if (prefix != null && signature.startsWith(prefix)) {
                signature = signature.substring(prefix.length());
            }
            
            return MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Calculate HMAC-SHA256 signature.
     */
    public static String calculateHmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_SHA256
            );
            mac.init(secretKey);
            
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }
    
    /**
     * Verify GitHub webhook signature.
     * Format: sha256=<hex_signature>
     */
    public static boolean verifyGitHub(String payload, String secret, String signature) {
        return verifyHmacSha256(payload, secret, signature, "sha256=");
    }
    
    /**
     * Verify Stripe webhook signature.
     * Format: t=<timestamp>,v1=<signature>
     */
    public static boolean verifyStripe(String payload, String secret, String signature) {
        try {
            // Extract v1 signature
            String[] parts = signature.split(",");
            String v1Signature = null;
            String timestamp = null;
            
            for (String part : parts) {
                if (part.startsWith("v1=")) {
                    v1Signature = part.substring(3);
                } else if (part.startsWith("t=")) {
                    timestamp = part.substring(2);
                }
            }
            
            if (v1Signature == null || timestamp == null) {
                return false;
            }
            
            // Stripe payload format: timestamp.payload
            String signedPayload = timestamp + "." + payload;
            return verifyHmacSha256(signedPayload, secret, v1Signature, null);
        } catch (Exception e) {
            return false;
        }
    }
}
