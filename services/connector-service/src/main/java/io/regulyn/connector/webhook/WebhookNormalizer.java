package io.regulyn.connector.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Service for normalizing webhook events from various providers.
 * Maps provider-specific event types to canonical types and extracts subjects.
 */
@Component
public class WebhookNormalizer {

    private static final Logger logger = LoggerFactory.getLogger(WebhookNormalizer.class);

    /**
     * Normalize webhook event based on provider.
     * 
     * @param provider Provider name (github, stripe, etc.)
     * @param headers Request headers (may contain event type)
     * @param payload Parsed JSON payload
     * @return Normalized event data
     */
    public NormalizedWebhook normalize(String provider, java.util.Map<String, String> headers, JsonNode payload) {
        NormalizedWebhook normalized = new NormalizedWebhook();

        switch (provider.toLowerCase()) {
            case "github":
                normalizeGitHub(headers, payload, normalized);
                break;
            case "stripe":
                normalizeStripe(headers, payload, normalized);
                break;
            default:
                normalizeGeneric(payload, normalized);
                break;
        }

        return normalized;
    }

    /**
     * Normalize GitHub webhook.
     * Event type: X-GitHub-Event header
     * Subject: repository.full_name or organization.login
     */
    private void normalizeGitHub(java.util.Map<String, String> headers, JsonNode payload, NormalizedWebhook result) {
        // Event type from header
        String eventType = getHeaderCaseInsensitive(headers, "X-GitHub-Event");
        if (eventType != null) {
            result.setNormalizedType("github." + eventType);
        } else {
            result.setNormalizedType("github.unknown");
        }

        // Subject: repository or organization
        String subject = null;
        if (payload.has("repository") && payload.get("repository").has("full_name")) {
            subject = payload.get("repository").get("full_name").asText();
        } else if (payload.has("organization") && payload.get("organization").has("login")) {
            subject = payload.get("organization").get("login").asText();
        }
        result.setNormalizedSubject(subject);
    }
    
    /**
     * Get header value case-insensitively.
     * HTTP headers are case-insensitive, but HashMap is case-sensitive.
     */
    private String getHeaderCaseInsensitive(java.util.Map<String, String> headers, String headerName) {
        if (headers == null || headerName == null) {
            return null;
        }
        // Try exact match first
        String value = headers.get(headerName);
        if (value != null) {
            return value;
        }
        // Try case-insensitive search
        String lowerHeaderName = headerName.toLowerCase();
        for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().toLowerCase().equals(lowerHeaderName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Normalize Stripe webhook.
     * Event type: payload.type field
     * Subject: payload.data.object.id or payload.data.object.customer
     */
    private void normalizeStripe(java.util.Map<String, String> headers, JsonNode payload, NormalizedWebhook result) {
        // Event type from payload
        if (payload.has("type")) {
            result.setNormalizedType("stripe." + payload.get("type").asText());
        } else {
            result.setNormalizedType("stripe.unknown");
        }

        // Subject: customer or object id
        String subject = null;
        if (payload.has("data") && payload.get("data").has("object")) {
            JsonNode obj = payload.get("data").get("object");
            if (obj.has("customer")) {
                subject = obj.get("customer").asText();
            } else if (obj.has("id")) {
                subject = obj.get("id").asText();
            }
        }
        result.setNormalizedSubject(subject);
    }

    /**
     * Normalize generic webhook (unknown provider).
     * Type: "generic.webhook"
     * Subject: extract "id" field if present
     */
    private void normalizeGeneric(JsonNode payload, NormalizedWebhook result) {
        result.setNormalizedType("generic.webhook");

        // Try to extract an ID field
        String subject = null;
        if (payload.has("id")) {
            subject = payload.get("id").asText();
        }
        result.setNormalizedSubject(subject);
    }

    /**
     * Container for normalized webhook data.
     */
    public static class NormalizedWebhook {
        private String normalizedType;
        private String normalizedSubject;

        public String getNormalizedType() {
            return normalizedType;
        }

        public void setNormalizedType(String normalizedType) {
            this.normalizedType = normalizedType;
        }

        public String getNormalizedSubject() {
            return normalizedSubject;
        }

        public void setNormalizedSubject(String normalizedSubject) {
            this.normalizedSubject = normalizedSubject;
        }
    }
}
