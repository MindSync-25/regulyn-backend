package io.regulyn.identity.controller;

import io.regulyn.identity.dto.TenantUsageIncrementRequest;
import io.regulyn.identity.dto.TenantUsageIncrementResponse;
import io.regulyn.identity.dto.ValidateApiKeyRequest;
import io.regulyn.identity.dto.ValidateApiKeyResponse;
import io.regulyn.identity.service.InternalApiKeyService;
import io.regulyn.identity.service.TenantUsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal endpoints for service-to-service communication.
 * Protected by InternalAuthFilter (X-Internal-Auth header).
 */
@RestController
@RequestMapping("/internal")
public class InternalController {

    private static final Logger log = LoggerFactory.getLogger(InternalController.class);
    private final InternalApiKeyService internalApiKeyService;
    private final TenantUsageService tenantUsageService;

    public InternalController(InternalApiKeyService internalApiKeyService,
                              TenantUsageService tenantUsageService) {
        this.internalApiKeyService = internalApiKeyService;
        this.tenantUsageService = tenantUsageService;
    }

    @PostMapping("/api-keys/validate")
    public ResponseEntity<ValidateApiKeyResponse> validateApiKey(@RequestBody ValidateApiKeyRequest request) {
        // NEVER log raw API keys
        log.debug("Received API key validation request");
        
        ValidateApiKeyResponse response = internalApiKeyService.validateApiKey(request.getApiKey());
        
        if (response.isValid()) {
            log.debug("API key validation successful for tenant: {}", response.getTenantId());
            return ResponseEntity.ok(response);
        } else {
            log.debug("API key validation failed");
            return ResponseEntity.status(403).body(ValidateApiKeyResponse.invalid());
        }
    }

    @PostMapping("/tenants/{tenantId}/usage/increment")
    public ResponseEntity<TenantUsageIncrementResponse> incrementUsage(@PathVariable("tenantId") UUID tenantId,
                                                                       @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
                                                                       @RequestBody TenantUsageIncrementRequest request) {
        TenantUsageIncrementResponse response = tenantUsageService.incrementUsage(tenantId, request, idempotencyKey);
        return ResponseEntity.ok(response);
    }
}
