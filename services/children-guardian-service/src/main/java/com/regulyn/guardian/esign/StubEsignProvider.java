package com.regulyn.guardian.esign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Component
public class StubEsignProvider implements EsignProvider {

    private static final String SIGNATURE_HEADER = "X-Stub-Signature";
    private static final String EVENT_TYPE_SIGNED = "DOCUMENT_SIGNED";

    private final ObjectMapper objectMapper;
    private final String hmacSecret;

    public StubEsignProvider(
            ObjectMapper objectMapper,
            @Value("${children.esign.stub.hmacSecret:test-secret}") String hmacSecret) {
        this.objectMapper = objectMapper;
        this.hmacSecret = hmacSecret;
    }

    @Override
    public ProviderId getProviderId() {
        return ProviderId.STUB;
    }

    @Override
    public EsignCreateRequestResult createSigningRequest(EsignCreateRequest cmd) {
        String envelopeSeed = String.join("|",
                cmd.tenantId().toString(),
                cmd.childId().toString(),
                cmd.guardianId().toString(),
                cmd.docType(),
                String.valueOf(cmd.docVersion()));
        String envelopeHash = sha256Hex(envelopeSeed.getBytes(StandardCharsets.UTF_8)).substring(0, 24);
        String providerEnvelopeId = "env_" + envelopeHash;
        String signingUrl = "https://stub-esign.local/sign/" + providerEnvelopeId;
        String requestPayloadHash = sha256Hex(envelopeSeed.getBytes(StandardCharsets.UTF_8));

        return new EsignCreateRequestResult(providerEnvelopeId, signingUrl, "CREATED", requestPayloadHash);
    }

    @Override
    public EsignWebhookParseResult parseAndVerifyWebhook(EsignWebhookRequest req) {
        byte[] rawBody = req.rawBody();
        String rawPayloadJson = new String(rawBody, StandardCharsets.UTF_8);
        String payloadSha256 = sha256Hex(rawBody);

        String signatureHeader = req.header(SIGNATURE_HEADER);
        String expectedSignature = hmacSha256Hex(hmacSecret, rawBody);

        EsignVerificationStatus verificationStatus = EsignVerificationStatus.VERIFIED;
        String verificationError = null;
        if (signatureHeader == null || signatureHeader.isBlank()) {
            verificationStatus = EsignVerificationStatus.FAILED;
            verificationError = "MISSING_SIGNATURE";
        } else if (!signatureHeader.equalsIgnoreCase(expectedSignature)) {
            verificationStatus = EsignVerificationStatus.FAILED;
            verificationError = "INVALID_SIGNATURE";
        }

        String providerEventId = null;
        String providerEnvelopeId = null;
        String eventType = null;
        String signedDocumentBase64 = null;
        String mime = null;
        String filename = null;

        try {
            JsonNode node = objectMapper.readTree(rawPayloadJson);
            providerEventId = textValue(node, "eventId");
            providerEnvelopeId = textValue(node, "envelopeId");
            eventType = textValue(node, "eventType");
            signedDocumentBase64 = textValue(node, "signedDocumentBase64");
            mime = textValue(node, "mime");
            filename = textValue(node, "filename");
        } catch (Exception e) {
            verificationStatus = EsignVerificationStatus.FAILED;
            verificationError = verificationError != null ? verificationError : "INVALID_PAYLOAD";
        }

        if (providerEventId == null || providerEventId.isBlank()) {
            providerEventId = "evt_" + payloadSha256.substring(0, 16);
        }
        if (providerEnvelopeId == null || providerEnvelopeId.isBlank()) {
            providerEnvelopeId = "env_" + payloadSha256.substring(0, 24);
        }

        Optional<SignedDoc> signedDoc = Optional.empty();
        if (verificationStatus == EsignVerificationStatus.VERIFIED
                && EVENT_TYPE_SIGNED.equalsIgnoreCase(eventType)
                && signedDocumentBase64 != null) {
            byte[] bytes = Base64.getDecoder().decode(signedDocumentBase64);
            signedDoc = Optional.of(new SignedDoc(bytes, mime, filename));
        }

        return new EsignWebhookParseResult(
                providerEventId,
                providerEnvelopeId,
                rawPayloadJson,
                payloadSha256,
                signatureHeader,
                verificationStatus,
                verificationError,
                signedDoc
        );
    }

    private String textValue(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child != null && !child.isNull() ? child.asText() : null;
    }

    private String hmacSha256Hex(String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmac = mac.doFinal(data);
            return toHex(hmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute SHA-256", e);
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
