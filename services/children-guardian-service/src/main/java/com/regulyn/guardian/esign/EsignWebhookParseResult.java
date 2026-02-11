package com.regulyn.guardian.esign;

import java.util.Optional;

public record EsignWebhookParseResult(
        String providerEventId,
        String providerEnvelopeId,
        String rawPayloadJson,
        String payloadSha256Hex,
        String signatureHeader,
        EsignVerificationStatus verificationStatus,
        String verificationError,
        Optional<SignedDoc> signedDoc
) {
}
