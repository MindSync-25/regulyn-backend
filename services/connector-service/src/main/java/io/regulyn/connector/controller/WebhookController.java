package io.regulyn.connector.controller;

import io.regulyn.connector.webhook.dto.WebhookResponse;
import io.regulyn.connector.webhook.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * Controller for receiving webhooks from external providers (GitHub, Stripe, etc.).
 * 
 * <p><strong>Endpoint:</strong> POST /api/v1/webhooks/{provider}/{connectorId}</p>
 * 
 * <p><strong>Signature Verification:</strong></p>
 * <ul>
 *   <li>GitHub: X-Hub-Signature-256 header with sha256= prefix</li>
 *   <li>Stripe: Stripe-Signature header with t=timestamp,v1=signature format</li>
 *   <li>Generic: X-Webhook-Signature header with HMAC-SHA256</li>
 * </ul>
 * 
 * <p><strong>Fail-Safe Behavior:</strong> Always returns 200 OK to prevent provider retries
 * even if signature validation fails. Invalid signatures are stored with signature_valid=false.</p>
 * 
 * <p><strong>Idempotency:</strong> Duplicate webhooks (same payload hash within 10 minutes)
 * are detected and return 200 OK without emitting duplicate outbox events.</p>
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * Receive webhook from external provider.
     * 
     * @param provider Provider name (github, stripe, generic)
     * @param connectorId Connector UUID
     * @param tenantId Optional tenant ID (prefer deriving from connector)
     * @param request HTTP request for extracting headers
     * @param rawPayload Raw request body bytes
     * @return WebhookResponse with correlation ID
     */
    @PostMapping("/{provider}/{connectorId}")
    public ResponseEntity<WebhookResponse> receiveWebhook(
            @PathVariable String provider,
            @PathVariable UUID connectorId,
            @RequestParam(required = false) UUID tenantId,
            HttpServletRequest request,
            @RequestBody byte[] rawPayload) {

        // Generate correlation ID from header or create new one
        String correlationId = getHeaderIgnoreCase(request, "X-Correlation-Id")
                .orElse(UUID.randomUUID().toString());

        log.info("Webhook received: provider={}, connectorId={}, correlationId={}, size={}",
                provider, connectorId, correlationId, rawPayload.length);

        try {
            // Extract all headers
            Map<String, String> headers = extractHeaders(request);

            // Process webhook (verification, normalization, persistence, events)
            WebhookService.WebhookProcessingResult result = webhookService.processWebhook(
                    provider,
                    connectorId,
                    headers,
                    rawPayload,
                    correlationId
            );

            // Return appropriate response
            if (result.isDuplicate()) {
                log.info("Duplicate webhook detected: correlationId={}", correlationId);
                return ResponseEntity.ok(WebhookResponse.duplicate(result.getCorrelationId()));
            }

            log.info("Webhook processed: correlationId={}, signatureValid={}",
                    correlationId, result.isSignatureValid());
            return ResponseEntity.ok(WebhookResponse.accepted(result.getCorrelationId()));

        } catch (Exception e) {
            // Log error but still return 200 OK to prevent provider retries
            log.error("Error processing webhook: provider={}, connectorId={}, correlationId={}",
                    provider, connectorId, correlationId, e);

            // Return 200 OK to prevent retries (fail-safe)
            return ResponseEntity.ok(WebhookResponse.builder()
                    .status("error")
                    .message("Webhook processing failed but was logged")
                    .correlationId(correlationId)
                    .build());
        }
    }

    /**
     * Extract all HTTP headers into a map.
     */
    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            headers.put(headerName, headerValue);
        }

        return headers;
    }

    /**
     * Get header value by name (case-insensitive).
     */
    private Optional<String> getHeaderIgnoreCase(HttpServletRequest request, String headerName) {
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (name.equalsIgnoreCase(headerName)) {
                return Optional.ofNullable(request.getHeader(name));
            }
        }

        return Optional.empty();
    }
}
