package com.regulyn.guardian.dto;

import java.util.UUID;

public record CreateEsignRequestResponse(
        UUID esignRequestId,
        String provider,
        String providerEnvelopeId,
        String signingUrl,
        String status
) {
}
