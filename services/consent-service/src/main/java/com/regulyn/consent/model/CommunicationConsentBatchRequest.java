package com.regulyn.consent.model;

import java.util.List;

public record CommunicationConsentBatchRequest(
        String channel,
        List<CommunicationConsentBatchRecipient> recipients
) {
}
