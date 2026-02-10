package com.regulyn.ropa.api;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.ropa.api.dto.*;
import com.regulyn.ropa.service.RetentionMatrixExportService;
import com.regulyn.ropa.service.RetentionPolicyCommandService;
import com.regulyn.ropa.service.RetentionResolutionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@RestController
@RequestMapping("/retention")
public class RetentionController {

    private final RetentionPolicyCommandService commandService;
    private final RetentionResolutionService resolutionService;
    private final RetentionMatrixExportService exportService;

    public RetentionController(RetentionPolicyCommandService commandService,
                               RetentionResolutionService resolutionService,
                               RetentionMatrixExportService exportService) {
        this.commandService = commandService;
        this.resolutionService = resolutionService;
        this.exportService = exportService;
    }

    @PutMapping("/systems/{systemId}")
    public ResponseEntity<RetentionPolicyUpsertResponse> upsertSystemRetention(
            @PathVariable("systemId") UUID systemId,
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RetentionPolicyUpsertRequest request) {
        requireIdempotency(idempotencyKey);
        ensureContext(tenantId, userId);
        RetentionPolicyUpsertResponse response = commandService.upsertSystemPolicy(tenantId, userId, idempotencyKey, systemId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/activities/{activityId}")
    public ResponseEntity<RetentionPolicyUpsertResponse> upsertActivityRetention(
            @PathVariable("activityId") UUID activityId,
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RetentionPolicyUpsertRequest request) {
        requireIdempotency(idempotencyKey);
        ensureContext(tenantId, userId);
        RetentionPolicyUpsertResponse response = commandService.upsertActivityPolicy(tenantId, userId, idempotencyKey, activityId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/categories/{dataCategoryId}/purposes/{purposeVersionId}")
    public ResponseEntity<RetentionPolicyUpsertResponse> upsertCategoryPurposeRetention(
            @PathVariable("dataCategoryId") UUID dataCategoryId,
            @PathVariable("purposeVersionId") UUID purposeVersionId,
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RetentionPolicyUpsertRequest request) {
        requireIdempotency(idempotencyKey);
        ensureContext(tenantId, userId);
        RetentionPolicyUpsertResponse response = commandService.upsertCategoryPurposePolicy(
            tenantId, userId, idempotencyKey, dataCategoryId, purposeVersionId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/resolve")
    public ResponseEntity<RetentionResolveResponse> resolveEffectiveRetention(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @RequestParam("systemId") UUID systemId,
            @RequestParam("activityId") UUID activityId,
            @RequestParam(value = "dataCategoryId", required = false) UUID dataCategoryId,
            @RequestParam(value = "purposeVersionId", required = false) UUID purposeVersionId,
            @RequestHeader(value = "X-Emit-Resolution-Audit", required = false, defaultValue = "false") boolean emitAudit) {

        if ((dataCategoryId == null) != (purposeVersionId == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dataCategoryId and purposeVersionId must be provided together");
        }

        ensureContext(tenantId, userId);
        RetentionResolveResponse response = resolutionService.resolveEffectiveRetention(
            tenantId, systemId, activityId, dataCategoryId, purposeVersionId, emitAudit);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/exports/matrix")
    public ResponseEntity<RetentionMatrixExportResponse> exportRetentionMatrix(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody RetentionMatrixExportRequest request) {
        requireIdempotency(idempotencyKey);
        ensureContext(tenantId, userId);
        RetentionMatrixExportResponse response = exportService.exportRetentionMatrix(tenantId, userId, idempotencyKey, request);
        return ResponseEntity.ok(response);
    }

    private void ensureContext(UUID tenantId, UUID userId) {
        TenantContext context = TenantContextHolder.getContext();
        if (context == null) {
            context = new TenantContext();
        }
        if (context.getTenantId() == null) {
            context.setTenantId(tenantId);
        }
        if (context.getUserId() == null) {
            context.setUserId(userId);
        }
        TenantContextHolder.setContext(context);
    }

    private void requireIdempotency(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Idempotency-Key is required");
        }
    }
}
