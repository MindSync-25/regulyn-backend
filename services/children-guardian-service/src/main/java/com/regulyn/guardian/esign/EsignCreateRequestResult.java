package com.regulyn.guardian.esign;

public record EsignCreateRequestResult(
        String providerEnvelopeId,
        String signingUrl,
        String status,
        String requestPayloadSha256
) {
}
