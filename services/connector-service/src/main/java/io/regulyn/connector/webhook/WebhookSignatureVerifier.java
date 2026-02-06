package io.regulyn.connector.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Service for verifying webhook signatures from various providers.
 * Supports HMAC-SHA256 verification for GitHub, Stripe, etc.
 */
@Component
public class WebhookSignatureVerifier {

    private static final Logger logger = LoggerFactory.getLogger(WebhookSignatureVerifier.class);

    /**
     * Verify GitHub-style webhook signature.
     * GitHub sends: X-Hub-Signature-256: sha256=<hex>
     * 
     * @param payload Raw request body bytes
     * @param signature Signature from X-Hub-Signature-256 header (with "sha256=" prefix)
     * @param secret Webhook secret
     * @return true if signature is valid
     */
    public boolean verifyGitHubSignature(byte[] payload, String signature, String secret) {
        if (signature == null || !signature.startsWith("sha256=")) {
            logger.debug("Invalid GitHub signature format");
            return false;
        }

        String expectedSig = signature.substring(7); // Remove "sha256=" prefix
        String computedSig = computeHmacSha256Hex(payload, secret);
        
        return MessageDigest.isEqual(
            expectedSig.getBytes(StandardCharsets.UTF_8),
            computedSig.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Verify Stripe-style webhook signature.
     * Stripe sends: Stripe-Signature: t=timestamp,v1=signature
     * 
     * @param payload Raw request body bytes
     * @param signature Signature from Stripe-Signature header
     * @param timestamp Timestamp from signature header
     * @param secret Webhook secret
     * @return true if signature is valid
     */
    public boolean verifyStripeSignature(byte[] payload, String signature, String timestamp, String secret) {
        if (signature == null || timestamp == null) {
            logger.debug("Missing Stripe signature or timestamp");
            return false;
        }

        // Extract v1 signature from header
        String v1Signature = null;
        for (String part : signature.split(",")) {
            if (part.startsWith("v1=")) {
                v1Signature = part.substring(3);
                break;
            }
        }

        if (v1Signature == null) {
            logger.debug("No v1 signature found in Stripe header");
            return false;
        }

        // Compute expected signature: HMAC-SHA256(timestamp.payload, secret)
        String signedPayload = timestamp + "." + new String(payload, StandardCharsets.UTF_8);
        String computedSig = computeHmacSha256Hex(signedPayload.getBytes(StandardCharsets.UTF_8), secret);

        return MessageDigest.isEqual(
            v1Signature.getBytes(StandardCharsets.UTF_8),
            computedSig.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Generic HMAC-SHA256 verification.
     * 
     * @param payload Raw request body bytes
     * @param signature Expected signature (hex or base64)
     * @param secret Webhook secret
     * @return true if signature is valid
     */
    public boolean verifyHmacSha256(byte[] payload, String signature, String secret) {
        if (signature == null) {
            return false;
        }

        String computedSig = computeHmacSha256Hex(payload, secret);
        
        // Try hex comparison first
        if (MessageDigest.isEqual(
            signature.getBytes(StandardCharsets.UTF_8),
            computedSig.getBytes(StandardCharsets.UTF_8)
        )) {
            return true;
        }

        // Try base64 comparison
        String computedBase64 = java.util.Base64.getEncoder().encodeToString(
            computeHmacSha256Bytes(payload, secret)
        );
        
        return MessageDigest.isEqual(
            signature.getBytes(StandardCharsets.UTF_8),
            computedBase64.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Compute HMAC-SHA256 signature as hex string.
     */
    private String computeHmacSha256Hex(byte[] payload, String secret) {
        byte[] sigBytes = computeHmacSha256Bytes(payload, secret);
        return HexFormat.of().formatHex(sigBytes);
    }

    /**
     * Compute HMAC-SHA256 signature as byte array.
     */
    private byte[] computeHmacSha256Bytes(byte[] payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            mac.init(secretKeySpec);
            return mac.doFinal(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256", e);
        }
    }

    /**
     * Compute SHA-256 hash of payload (for payload_hash field).
     */
    public String computePayloadHash(byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256", e);
        }
    }
}
