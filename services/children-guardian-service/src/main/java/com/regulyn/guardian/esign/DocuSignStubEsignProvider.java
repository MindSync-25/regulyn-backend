package com.regulyn.guardian.esign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

@Component
public class DocuSignStubEsignProvider implements EsignProvider {

    private final ObjectMapper objectMapper;

    public DocuSignStubEsignProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ProviderId getProviderId() {
        return ProviderId.DOCUSIGN;
    }

    @Override
    public EsignCreateRequestResult createSigningRequest(EsignCreateRequest cmd) {
        String seed = String.join("|",
                cmd.tenantId().toString(),
                cmd.childId().toString(),
                cmd.guardianId().toString(),
                cmd.docType(),
                String.valueOf(cmd.docVersion()),
                "DOCUSIGN");
        String envelopeHash = sha256Hex(seed.getBytes(StandardCharsets.UTF_8)).substring(0, 24);
        String providerEnvelopeId = "ds_env_" + envelopeHash;
        String signingUrl = "https://docusign.example/sign/" + providerEnvelopeId;

        return new EsignCreateRequestResult(providerEnvelopeId, signingUrl, "CREATED", null);
    }

    @Override
    public EsignWebhookParseResult parseAndVerifyWebhook(EsignWebhookRequest req) {
        byte[] rawBody = req.rawBody();
        String rawPayloadJson = new String(rawBody, StandardCharsets.UTF_8);
        String payloadSha256 = sha256Hex(rawBody);

        String providerEventId = null;
        String providerEnvelopeId = null;
        try {
            JsonNode node = objectMapper.readTree(rawPayloadJson);
            providerEventId = readAny(node, "eventId", "event_id", "eventID");
            providerEnvelopeId = readAny(node, "envelopeId", "envelope_id", "envelopeID");
        } catch (Exception ignored) {
        }

        if (providerEventId == null || providerEventId.isBlank()) {
            providerEventId = "ds_evt_" + payloadSha256.substring(0, 16);
        }
        if (providerEnvelopeId == null || providerEnvelopeId.isBlank()) {
            providerEnvelopeId = "ds_env_" + payloadSha256.substring(0, 24);
        }

        return new EsignWebhookParseResult(
                providerEventId,
                providerEnvelopeId,
                rawPayloadJson,
                payloadSha256,
                null,
                EsignVerificationStatus.SKIPPED,
                "SIGNATURE_SKIPPED",
                Optional.empty()
        );
    }

    private String readAny(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode val = node.get(field);
            if (val != null && !val.isNull() && !val.asText().isBlank()) {
                return val.asText();
            }
        }
        return null;
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest(data)) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute SHA-256", e);
        }
    }
}
