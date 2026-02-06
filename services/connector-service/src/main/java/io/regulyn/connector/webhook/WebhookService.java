package io.regulyn.connector.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.events.outbox.OutboxEvent;
import com.regulyn.events.outbox.OutboxEventRepository;
import io.regulyn.connector.credentials.CredentialResolutionService;
import io.regulyn.connector.credentials.ResolvedCredentials;
import io.regulyn.connector.model.Connector;
import io.regulyn.connector.model.WebhookEvent;
import io.regulyn.connector.repository.ConnectorRepository;
import io.regulyn.connector.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Service for processing incoming webhooks.
 * Handles verification, normalization, persistence, and event emission.
 */
@Service
public class WebhookService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);
    private static final long DUPLICATE_WINDOW_MS = 10 * 60 * 1000; // 10 minutes

    private final ConnectorRepository connectorRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final CredentialResolutionService credentialResolutionService;
    private final WebhookSignatureVerifier signatureVerifier;
    private final WebhookNormalizer normalizer;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WebhookService(
            ConnectorRepository connectorRepository,
            WebhookEventRepository webhookEventRepository,
            OutboxEventRepository outboxEventRepository,
            CredentialResolutionService credentialResolutionService,
            WebhookSignatureVerifier signatureVerifier,
            WebhookNormalizer normalizer,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.connectorRepository = connectorRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.credentialResolutionService = credentialResolutionService;
        this.signatureVerifier = signatureVerifier;
        this.normalizer = normalizer;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Process incoming webhook.
     * 
     * @param provider Provider name (github, stripe, etc.)
     * @param connectorId Connector ID
     * @param headers Request headers (including signature)
     * @param rawPayload Raw request body bytes
     * @param correlationId Optional correlation ID from header
     * @return Processing result with correlation ID
     */
    @Transactional
    public WebhookProcessingResult processWebhook(
            String provider,
            UUID connectorId,
            Map<String, String> headers,
            byte[] rawPayload,
            String correlationId) {

        // Generate correlation ID if not provided
        correlationId = normalizeCorrelationId(correlationId, "webhook-");

        try {
            // 1. Look up connector and derive tenant
            Connector connector = connectorRepository.findById(connectorId)
                    .orElseThrow(() -> new WebhookException("Connector not found: " + connectorId));
            UUID tenantId = connector.getTenantId();

            // 2. Compute payload hash
            String payloadHash = signatureVerifier.computePayloadHash(rawPayload);

            // 3. Check for duplicates
            if (isDuplicate(tenantId, connectorId, payloadHash)) {
                logger.info("Duplicate webhook detected: tenant={}, connector={}, hash={}", 
                    tenantId, connectorId, payloadHash);
                return new WebhookProcessingResult(correlationId, true, true);
            }

            // 4. Verify signature
            boolean signatureValid = verifySignature(provider, tenantId, connectorId, headers, rawPayload, correlationId);

            // 5. Parse and normalize payload
            JsonNode payload = objectMapper.readTree(rawPayload);
            WebhookNormalizer.NormalizedWebhook normalized = normalizer.normalize(provider, headers, payload);

            // 6. Persist webhook event
            WebhookEvent event = persistWebhookEvent(
                tenantId, connectorId, provider, headers, rawPayload, payload,
                payloadHash, signatureValid, normalized, correlationId
            );

            // 7. Emit outbox events
            emitWebhookReceivedEvent(event);
            if (!signatureValid) {
                emitWebhookSignatureInvalidEvent(event);
            }
            if (normalized.getNormalizedType() != null) {
                emitWebhookNormalizedEvent(event, normalized);
            }

            logger.info("Webhook processed successfully: tenant={}, connector={}, correlation={}, signatureValid={}", 
                tenantId, connectorId, correlationId, signatureValid);

            return new WebhookProcessingResult(correlationId, false, signatureValid);

        } catch (Exception e) {
            logger.error("Failed to process webhook: provider={}, connector={}, correlation={}", 
                provider, connectorId, correlationId, e);
            throw new WebhookException("Failed to process webhook", e);
        }
    }

    /**
     * Check if webhook is a duplicate within the time window.
     */
    private boolean isDuplicate(UUID tenantId, UUID connectorId, String payloadHash) {
        Instant windowStart = Instant.now().minusMillis(DUPLICATE_WINDOW_MS);
        long count = webhookEventRepository.countByTenantIdAndConnectorIdAndPayloadHashAndReceivedAtAfter(
            tenantId, connectorId, payloadHash, windowStart
        );
        return count > 0;
    }

    /**
     * Verify webhook signature based on provider.
     */
    private boolean verifySignature(
            String provider,
            UUID tenantId,
            UUID connectorId,
            Map<String, String> headers,
            byte[] rawPayload,
            String correlationId) {

        try {
            // Resolve webhook secret from credentials
            ResolvedCredentials credentials = credentialResolutionService.resolve(
                tenantId, connectorId, correlationId
            );

            String secret = credentials.get("webhook_secret");
            if (secret == null || secret.isEmpty()) {
                logger.warn("No webhook_secret found in credentials for connector: {}", connectorId);
                return false;
            }

            // Verify based on provider
            switch (provider.toLowerCase()) {
                case "github":
                    String githubSig = getHeaderCaseInsensitive(headers, "X-Hub-Signature-256");
                    return signatureVerifier.verifyGitHubSignature(rawPayload, githubSig, secret);

                case "stripe":
                    String stripeSig = getHeaderCaseInsensitive(headers, "Stripe-Signature");
                    String timestamp = extractStripeTimestamp(stripeSig);
                    return signatureVerifier.verifyStripeSignature(rawPayload, stripeSig, timestamp, secret);

                default:
                    // Generic HMAC-SHA256 verification
                    String genericSig = getHeaderCaseInsensitive(headers, "X-Webhook-Signature");
                    if (genericSig == null) {
                        logger.debug("No signature header found for provider: {}", provider);
                        return false;
                    }
                    return signatureVerifier.verifyHmacSha256(rawPayload, genericSig, secret);
            }

        } catch (Exception e) {
            logger.error("Signature verification failed", e);
            return false;
        }
    }

    /**
     * Extract timestamp from Stripe signature header.
     */
    private String extractStripeTimestamp(String signature) {
        if (signature == null) {
            return null;
        }
        for (String part : signature.split(",")) {
            if (part.startsWith("t=")) {
                return part.substring(2);
            }
        }
        return null;
    }
    
    /**
     * Get header value case-insensitively.
     * HTTP headers are case-insensitive, but HashMap is case-sensitive.
     */
    private String getHeaderCaseInsensitive(Map<String, String> headers, String headerName) {
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
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().toLowerCase().equals(lowerHeaderName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Persist webhook event to database.
     */
    private WebhookEvent persistWebhookEvent(
            UUID tenantId,
            UUID connectorId,
            String provider,
            Map<String, String> headers,
            byte[] rawPayload,
            JsonNode payload,
            String payloadHash,
            boolean signatureValid,
            WebhookNormalizer.NormalizedWebhook normalized,
            String correlationId) throws Exception {

        WebhookEvent event = new WebhookEvent();
        event.setTenantId(tenantId);
        event.setConnectorId(connectorId);
        event.setProvider(provider);
        event.setSignatureValid(signatureValid);
        event.setPayloadHash(payloadHash);
        event.setCorrelationId(correlationId);
        event.setReceivedAt(Instant.now());

        // Store headers as JSON (remove sensitive headers)
        Map<String, String> safeHeaders = new HashMap<>(headers);
        safeHeaders.remove("authorization");
        safeHeaders.remove("x-webhook-signature");
        safeHeaders.remove("x-hub-signature-256");
        safeHeaders.remove("stripe-signature");
        
        // Convert to Map<String, Object> for JSONB storage
        Map<String, Object> headersMap = new HashMap<>(safeHeaders);
        event.setHeadersJson(headersMap);

        // Store raw payload as Map<String, Object>
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payloadMap = objectMapper.readValue(rawPayload, Map.class);
            event.setRawPayloadJson(payloadMap);
        } catch (Exception e) {
            // If not valid JSON, store as wrapper
            Map<String, Object> wrapperMap = new HashMap<>();
            wrapperMap.put("raw", new String(rawPayload, java.nio.charset.StandardCharsets.UTF_8));
            event.setRawPayloadJson(wrapperMap);
        }

        // Set normalized fields
        if (normalized.getNormalizedType() != null) {
            event.setNormalizedType(normalized.getNormalizedType());
        }
        if (normalized.getNormalizedSubject() != null) {
            event.setNormalizedSubject(normalized.getNormalizedSubject());
        }

        return webhookEventRepository.save(event);
    }

    /**
     * Emit WEBHOOK_RECEIVED event.
     */
    private void emitWebhookReceivedEvent(WebhookEvent event) {
        OutboxEvent outbox = new OutboxEvent();
        outbox.setTenantId(event.getTenantId());
        outbox.setEventId(UUID.randomUUID());
        outbox.setEventType("WEBHOOK_RECEIVED");
        outbox.setSourceService("connector-service");
        outbox.setEntityType("WEBHOOK_EVENT");
        outbox.setEntityId(event.getId().toString());
        outbox.setOccurredAt(Instant.now());
        outbox.setCorrelationId(event.getCorrelationId());
        outbox.setStatus(com.regulyn.events.outbox.OutboxStatus.PENDING);
        outbox.setNextAttemptAt(Instant.now());

        String payloadJson = null;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("webhookId", event.getId().toString());
            payload.put("connectorId", event.getConnectorId().toString());
            payload.put("provider", event.getProvider());
            payload.put("correlationId", event.getCorrelationId());
            payload.put("payloadHash", event.getPayloadHash());
            payload.put("signatureValid", event.getSignatureValid());
            if (event.getNormalizedType() != null) {
                payload.put("normalizedType", event.getNormalizedType());
            }

            payloadJson = objectMapper.writeValueAsString(payload);
            outbox.setPayload(payloadJson);
            outbox.setPayloadHash(Integer.toString(payloadJson.hashCode()));
        } catch (Exception e) {
            logger.error("Failed to serialize WEBHOOK_RECEIVED payload", e);
        }

        outboxEventRepository.saveAndFlush(outbox);
        writeAuditEvent("WEBHOOK_RECEIVED", event, payloadJson, null);
    }

    /**
     * Emit WEBHOOK_SIGNATURE_INVALID event.
     */
    private void emitWebhookSignatureInvalidEvent(WebhookEvent event) {
        OutboxEvent outbox = new OutboxEvent();
        outbox.setTenantId(event.getTenantId());
        outbox.setEventId(UUID.randomUUID());
        outbox.setEventType("WEBHOOK_SIGNATURE_INVALID");
        outbox.setSourceService("connector-service");
        outbox.setEntityType("WEBHOOK_EVENT");
        outbox.setEntityId(event.getId().toString());
        outbox.setOccurredAt(Instant.now());
        outbox.setCorrelationId(event.getCorrelationId());
        outbox.setStatus(com.regulyn.events.outbox.OutboxStatus.PENDING);
        outbox.setNextAttemptAt(Instant.now());

        String payloadJson = null;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("webhookId", event.getId().toString());
            payload.put("connectorId", event.getConnectorId().toString());
            payload.put("provider", event.getProvider());
            payload.put("correlationId", event.getCorrelationId());
            payload.put("payloadHash", event.getPayloadHash());

            payloadJson = objectMapper.writeValueAsString(payload);
            outbox.setPayload(payloadJson);
            outbox.setPayloadHash(Integer.toString(payloadJson.hashCode()));
        } catch (Exception e) {
            logger.error("Failed to serialize WEBHOOK_SIGNATURE_INVALID payload", e);
        }

        outboxEventRepository.saveAndFlush(outbox);
        writeAuditEvent("WEBHOOK_SIGNATURE_INVALID", event, payloadJson, null);
    }

    /**
     * Emit WEBHOOK_NORMALIZED event.
     */
    private void emitWebhookNormalizedEvent(WebhookEvent event, WebhookNormalizer.NormalizedWebhook normalized) {
        OutboxEvent outbox = new OutboxEvent();
        outbox.setTenantId(event.getTenantId());
        outbox.setEventId(UUID.randomUUID());
        outbox.setEventType("WEBHOOK_NORMALIZED");
        outbox.setSourceService("connector-service");
        outbox.setEntityType("WEBHOOK_EVENT");
        outbox.setEntityId(event.getId().toString());
        outbox.setOccurredAt(Instant.now());
        outbox.setCorrelationId(event.getCorrelationId());
        outbox.setStatus(com.regulyn.events.outbox.OutboxStatus.PENDING);
        outbox.setNextAttemptAt(Instant.now());

        String payloadJson = null;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("webhookId", event.getId().toString());
            payload.put("connectorId", event.getConnectorId().toString());
            payload.put("provider", event.getProvider());
            payload.put("correlationId", event.getCorrelationId());
            payload.put("normalizedType", normalized.getNormalizedType());
            if (normalized.getNormalizedSubject() != null) {
                payload.put("normalizedSubject", normalized.getNormalizedSubject());
            }

            payloadJson = objectMapper.writeValueAsString(payload);
            outbox.setPayload(payloadJson);
            outbox.setPayloadHash(Integer.toString(payloadJson.hashCode()));
        } catch (Exception e) {
            logger.error("Failed to serialize WEBHOOK_NORMALIZED payload", e);
        }

        outboxEventRepository.saveAndFlush(outbox);
        writeAuditEvent("WEBHOOK_NORMALIZED", event, payloadJson, null);
    }

    private void writeAuditEvent(String action, WebhookEvent event, String payloadJson, String evidenceId) {
        try {
            String sql = """
                INSERT INTO connector.audit_events (
                    event_id, tenant_id, occurred_at, actor_id, actor_type, service,
                    action, entity_type, entity_id, payload_hash, evidence_id, metadata
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """;

            jdbcTemplate.update(
                    sql,
                    UUID.randomUUID(),
                    event.getTenantId(),
                    java.sql.Timestamp.from(Instant.now()),
                    null,
                    "SYSTEM",
                    "connector-service",
                    action,
                    "WEBHOOK_EVENT",
                    event.getId().toString(),
                    Integer.toString(payloadJson.hashCode()),
                    evidenceId,
                    payloadJson
            );
        } catch (Exception e) {
            logger.error("Failed to write audit event {} for webhook {}", action, event.getId(), e);
        }
    }

    private String normalizeCorrelationId(String correlationId, String prefix) {
        String value = correlationId;
        if (value == null || value.isBlank()) {
            value = prefix + UUID.randomUUID();
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    /**
     * Result of webhook processing.
     */
    public static class WebhookProcessingResult {
        private final String correlationId;
        private final boolean duplicate;
        private final boolean signatureValid;

        public WebhookProcessingResult(String correlationId, boolean duplicate, boolean signatureValid) {
            this.correlationId = correlationId;
            this.duplicate = duplicate;
            this.signatureValid = signatureValid;
        }

        public String getCorrelationId() {
            return correlationId;
        }

        public boolean isDuplicate() {
            return duplicate;
        }

        public boolean isSignatureValid() {
            return signatureValid;
        }
    }

    /**
     * Exception for webhook processing errors.
     */
    public static class WebhookException extends RuntimeException {
        public WebhookException(String message) {
            super(message);
        }

        public WebhookException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
