package com.regulyn.consent.model;

import java.util.List;

public record CommunicationConsentBatchResponse(
        String channel,
        boolean failClosed,
        List<CommunicationConsentBatchResult> results
) {
}
