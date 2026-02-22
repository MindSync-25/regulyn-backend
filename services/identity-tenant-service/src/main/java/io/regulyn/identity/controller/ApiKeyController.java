package io.regulyn.identity.controller;

import io.regulyn.identity.dto.ApiKeyCreateRequest;
import io.regulyn.identity.dto.ApiKeyCreateResponse;
import io.regulyn.identity.dto.ApiKeyListItem;
import io.regulyn.identity.dto.ApiKeyRevokeRequest;
import io.regulyn.identity.dto.ApiKeyRevokeResponse;
import io.regulyn.identity.dto.ApiKeyRotateResponse;
import io.regulyn.identity.service.ApiKeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<List<ApiKeyListItem>> listApiKeys() {
        return ResponseEntity.ok(apiKeyService.listApiKeys());
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiKeyCreateResponse> createApiKey(
            @RequestBody(required = false) ApiKeyCreateRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        ApiKeyCreateResponse response = apiKeyService.createApiKey(request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{apiKeyId}/rotate")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiKeyRotateResponse> rotateApiKey(
            @PathVariable("apiKeyId") UUID apiKeyId,
            @RequestBody(required = false) ApiKeyCreateRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        ApiKeyRotateResponse response = apiKeyService.rotateApiKey(apiKeyId, request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{apiKeyId}/revoke")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiKeyRevokeResponse> revokeApiKey(
            @PathVariable("apiKeyId") UUID apiKeyId,
            @RequestBody(required = false) ApiKeyRevokeRequest request) {
        ApiKeyRevokeResponse response = apiKeyService.revokeApiKey(apiKeyId, request);
        return ResponseEntity.ok(response);
    }
}
