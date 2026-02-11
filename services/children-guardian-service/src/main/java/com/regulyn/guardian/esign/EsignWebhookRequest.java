package com.regulyn.guardian.esign;

import java.util.Map;
import java.util.UUID;

public record EsignWebhookRequest(
        UUID tenantId,
        ProviderId providerId,
        Map<String, String> headers,
        byte[] rawBody
) {
    public String header(String name) {
        if (headers == null) {
            return null;
        }
        return headers.get(name);
    }
}
