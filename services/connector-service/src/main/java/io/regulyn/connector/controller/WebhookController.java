package io.regulyn.connector.controller;

import io.regulyn.connector.model.WebhookEvent;
import io.regulyn.connector.service.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Webhook receiver controller.
 * Endpoint: POST /api/v1/webhooks/{provider}/{connectorId}
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {
    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);
    
    private final WebhookService webhookService;
    
    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }
    
    /**
     * Receive webhook from external provider.
     * 
     * @param provider Provider name (github, stripe, etc.)
     * @param connectorId Connector identifier
     * @param signature Webhook signature header (provider-specific)
     * @param payload Webhook payload
     * @return Success response
     */
    @PostMapping("/{provider}/{connectorId}")
    public ResponseEntity<Map<String, Object>> receiveWebhook(
        @PathVariable String provider,
        @PathVariable UUID connectorId,
        @RequestHeader(value = "X-Hub-Signature-256", required = false) String githubSignature,
        @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature,
        @RequestHeader(value = "X-Webhook-Signature", required = false) String genericSignature,
        @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId,
        @RequestBody String payload
    ) {
        logger.info("Received webhook from provider: {} for connector: {}", provider, connectorId);
        
        // Determine signature based on provider
        String signature = switch (provider.toLowerCase()) {
            case "github" -> githubSignature;
            case "stripe" -> stripeSignature;
            default -> genericSignature;
        };
        
        // If tenant ID not provided in header, extract from connector or use default
        if (tenantId == null) {
            // In production, fetch tenant ID from connector
            tenantId = UUID.randomUUID(); // Placeholder
            logger.warn("Tenant ID not provided in webhook, using placeholder");
        }
        
        try {
            WebhookEvent webhookEvent = webhookService.processWebhook(
                tenantId,
                connectorId,
                provider,
                signature,
                payload
            );
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "webhook_event_id", webhookEvent.getWebhookEventId().toString(),
                "signature_verified", webhookEvent.isSignatureVerified()
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid webhook payload", e);
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "status", "error",
                    "message", "Invalid webhook payload"
                ));
        } catch (Exception e) {
            logger.error("Failed to process webhook", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to process webhook"
                ));
        }
    }
    
    /**
     * Health check endpoint for webhook configuration testing.
     */
    @GetMapping("/{provider}/{connectorId}/health")
    public ResponseEntity<Map<String, String>> webhookHealth(
        @PathVariable String provider,
        @PathVariable UUID connectorId
    ) {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "provider", provider,
            "connector_id", connectorId.toString()
        ));
    }
}
