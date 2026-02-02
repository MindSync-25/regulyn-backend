package io.regulyn.connector.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.outbox.OutboxEvent;
import com.regulyn.events.outbox.OutboxEventRepository;
import io.regulyn.connector.credentials.CredentialService;
import io.regulyn.connector.credentials.ResolvedCredential;
import io.regulyn.connector.model.ConnectorRun;
import io.regulyn.connector.model.WebhookEvent;
import io.regulyn.connector.repository.ConnectorRunRepository;
import io.regulyn.connector.repository.WebhookEventRepository;
import io.regulyn.connector.webhook.WebhookSignatureVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Service for processing webhook events from external providers.
 */
@Service
public class WebhookService {
    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);
    
    private final WebhookEventRepository webhookEventRepository;
    private final ConnectorRunRepository runRepository;
    private final OutboxEventRepository outboxRepository;
    private final AuditWriter auditWriter;
    private final CredentialService credentialService;
    private final ObjectMapper objectMapper;
    
    public WebhookService(
        WebhookEventRepository webhookEventRepository,
        ConnectorRunRepository runRepository,
        OutboxEventRepository outboxRepository,
        AuditWriter auditWriter,
        CredentialService credentialService,
        ObjectMapper objectMapper
    ) {
        this.webhookEventRepository = webhookEventRepository;
        this.runRepository = runRepository;
        this.outboxRepository = outboxRepository;
        this.auditWriter = auditWriter;
        this.credentialService = credentialService;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Process incoming webhook event.
     * 
     * @param tenantId Tenant identifier
     * @param connectorId Connector identifier
     * @param provider Provider name (github, stripe, etc.)
     * @param signature Webhook signature from header
     * @param payload Raw webhook payload
     * @return Created webhook event
     */
    @Transactional
    public WebhookEvent processWebhook(
        UUID tenantId,
        UUID connectorId,
        String provider,
        String signature,
        String payload
    ) {
        // Parse payload
        Map<String, Object> payloadMap;
        try {
            payloadMap = objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid webhook payload", e);
        }
        
        // Extract event type (provider-specific)
        String eventType = extractEventType(provider, payloadMap);
        
        // Verify signature if provided
        boolean signatureVerified = false;
        if (signature != null && !signature.isBlank()) {
            signatureVerified = verifySignature(tenantId, connectorId, provider, payload, signature);
            
            if (!signatureVerified) {
                logger.warn("Webhook signature verification failed for connector: {}, provider: {}", 
                    connectorId, provider);
            }
        }
        
        // Store webhook event
        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.setTenantId(tenantId);
        webhookEvent.setConnectorId(connectorId);
        webhookEvent.setProvider(provider);
        webhookEvent.setEventType(eventType);
        webhookEvent.setNormalizedEventType(normalizeEventType(provider, eventType));
        webhookEvent.setSignature(signature);
        webhookEvent.setSignatureVerified(signatureVerified);
        webhookEvent.setRawPayload(payloadMap);
        webhookEvent.setProcessed(false);
        
        webhookEvent = webhookEventRepository.save(webhookEvent);
        
        logger.info("Stored webhook event: {} from provider: {} for connector: {}", 
            webhookEvent.getWebhookEventId(), provider, connectorId);
        
        // Emit audit event
        auditWriter.auditAction(
            "WEBHOOK_RECEIVED",
            "WEBHOOK_EVENT",
            webhookEvent.getWebhookEventId().toString(),
            "N/A",
            null,
            null
        );
        
        // Emit outbox event
        try {
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setTenantId(tenantId);
            outboxEvent.setEventId(UUID.randomUUID());
            outboxEvent.setEventType("WEBHOOK_RECEIVED");
            outboxEvent.setSourceService("connector-service");
            outboxEvent.setEntityType("WEBHOOK_EVENT");
            outboxEvent.setEntityId(webhookEvent.getWebhookEventId().toString());
            outboxEvent.setOccurredAt(Instant.now());
            outboxEvent.setCorrelationId(webhookEvent.getWebhookEventId().toString());
            outboxEvent.setNextAttemptAt(Instant.now());
            
            String payloadJson = objectMapper.writeValueAsString(Map.of(
                "webhook_event_id", webhookEvent.getWebhookEventId().toString(),
                "connector_id", connectorId.toString(),
                "provider", provider,
                "event_type", eventType,
                "signature_verified", signatureVerified
            ));
            outboxEvent.setPayload(payloadJson);
            outboxEvent.setPayloadHash(Integer.toString(payloadJson.hashCode()));
            
            outboxRepository.save(outboxEvent);
        } catch (Exception e) {
            logger.error("Failed to create outbox event", e);
        }
        
        // Create connector run for webhook processing
        createWebhookRun(tenantId, connectorId, webhookEvent);
        
        return webhookEvent;
    }
    
    private void createWebhookRun(UUID tenantId, UUID connectorId, WebhookEvent webhookEvent) {
        ConnectorRun run = new ConnectorRun();
        run.setTenantId(tenantId);
        run.setConnectorId(connectorId);
        run.setRunType("WEBHOOK");
        run.setWebhookEventId(webhookEvent.getWebhookEventId());
        run.setJobType("WEBHOOK_PROCESS");
        run.setStatus("PENDING");
        run.setIdempotencyKey("webhook:" + webhookEvent.getWebhookEventId());
        
        run = runRepository.save(run);
        
        logger.info("Created webhook connector run: {} for webhook event: {}", 
            run.getRunId(), webhookEvent.getWebhookEventId());
        
        // Emit outbox event for run creation
        try {
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setTenantId(tenantId);
            outboxEvent.setEventId(UUID.randomUUID());
            outboxEvent.setEventType("CONNECTOR_RUN_CREATED");
            outboxEvent.setSourceService("connector-service");
            outboxEvent.setEntityType("CONNECTOR_RUN");
            outboxEvent.setEntityId(run.getRunId().toString());
            outboxEvent.setOccurredAt(Instant.now());
            outboxEvent.setCorrelationId(run.getRunId().toString());
            outboxEvent.setNextAttemptAt(Instant.now());
            
            String payloadJson = objectMapper.writeValueAsString(Map.of(
                "run_id", run.getRunId().toString(),
                "run_type", "WEBHOOK",
                "webhook_event_id", webhookEvent.getWebhookEventId().toString(),
                "connector_id", connectorId.toString(),
                "status", "PENDING"
            ));
            outboxEvent.setPayload(payloadJson);
            outboxEvent.setPayloadHash(Integer.toString(payloadJson.hashCode()));
            
            outboxRepository.save(outboxEvent);
        } catch (Exception e) {
            logger.error("Failed to create outbox event", e);
        }
    }
    
    private boolean verifySignature(
        UUID tenantId, 
        UUID connectorId, 
        String provider,
        String payload, 
        String signature
    ) {
        try {
            // Get webhook secret from credentials
            ResolvedCredential credential = credentialService.resolveCredential(
                tenantId, 
                connectorId, 
                "HMAC_SECRET"
            );
            
            String secret = credential.value();
            
            // Verify based on provider
            return switch (provider.toLowerCase()) {
                case "github" -> WebhookSignatureVerifier.verifyGitHub(payload, secret, signature);
                case "stripe" -> WebhookSignatureVerifier.verifyStripe(payload, secret, signature);
                default -> WebhookSignatureVerifier.verifyHmacSha256(payload, secret, signature, "sha256=");
            };
        } catch (Exception e) {
            logger.error("Failed to verify webhook signature", e);
            return false;
        }
    }
    
    private String extractEventType(String provider, Map<String, Object> payload) {
        return switch (provider.toLowerCase()) {
            case "github" -> (String) payload.getOrDefault("action", "unknown");
            case "stripe" -> (String) payload.getOrDefault("type", "unknown");
            default -> (String) payload.getOrDefault("event_type", "unknown");
        };
    }
    
    private String normalizeEventType(String provider, String eventType) {
        // Normalize provider-specific event types to common types
        return switch (provider.toLowerCase() + ":" + eventType) {
            case "github:push" -> "DATA_CHANGED";
            case "github:pull_request" -> "DATA_CHANGED";
            case "stripe:payment_intent.succeeded" -> "PAYMENT_COMPLETED";
            case "stripe:customer.created" -> "ENTITY_CREATED";
            default -> "CUSTOM_EVENT";
        };
    }
}
