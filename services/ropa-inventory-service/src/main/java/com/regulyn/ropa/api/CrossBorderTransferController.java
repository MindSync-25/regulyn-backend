package com.regulyn.ropa.api;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.ropa.api.dto.*;
import com.regulyn.ropa.service.CrossBorderReportExportService;
import com.regulyn.ropa.service.CrossBorderTransferCommandService;
import com.regulyn.ropa.service.CrossBorderTransferQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cross-border")
public class CrossBorderTransferController {

    private final CrossBorderTransferCommandService commandService;
    private final CrossBorderTransferQueryService queryService;
    private final CrossBorderReportExportService reportExportService;

    public CrossBorderTransferController(CrossBorderTransferCommandService commandService,
                                         CrossBorderTransferQueryService queryService,
                                         CrossBorderReportExportService reportExportService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.reportExportService = reportExportService;
    }

    @PutMapping("/transfers")
    public ResponseEntity<CrossBorderTransferUpsertResponse> upsertTransfer(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody CrossBorderTransferUpsertRequest request) {
        requireIdempotency(idempotencyKey);
        ensureContext(tenantId, userId);
        CrossBorderTransferUpsertResponse response = commandService.upsertTransfer(tenantId, userId, idempotencyKey, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transfers/{transferId}")
    public ResponseEntity<CrossBorderTransferResponse> getTransfer(
            @PathVariable("transferId") UUID transferId,
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId) {
        ensureContext(tenantId, userId);
        CrossBorderTransferResponse response = queryService.getTransfer(tenantId, transferId);
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer not found");
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transfers")
    public ResponseEntity<List<CrossBorderTransferResponse>> queryTransfers(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @RequestParam(value = "vendorId", required = false) UUID vendorId,
            @RequestParam(value = "systemId", required = false) UUID systemId,
            @RequestParam(value = "activityId", required = false) UUID activityId,
            @RequestParam(value = "sourceRegion", required = false) String sourceRegion,
            @RequestParam(value = "destinationRegion", required = false) String destinationRegion,
            @RequestParam(value = "dataCategoryId", required = false) UUID dataCategoryId,
            @RequestParam(value = "purposeVersionId", required = false) UUID purposeVersionId,
            @RequestParam(value = "activeOnly", required = false) Boolean activeOnly) {
        ensureContext(tenantId, userId);
        CrossBorderTransferFilters filters = new CrossBorderTransferFilters();
        filters.setVendorId(vendorId);
        filters.setSystemId(systemId);
        filters.setActivityId(activityId);
        filters.setSourceRegion(sourceRegion);
        filters.setDestinationRegion(destinationRegion);
        filters.setDataCategoryId(dataCategoryId);
        filters.setPurposeVersionId(purposeVersionId);
        filters.setActiveOnly(activeOnly);
        List<CrossBorderTransferResponse> response = queryService.queryTransfers(tenantId, filters);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/exports/report")
    public ResponseEntity<CrossBorderReportExportResponse> exportReport(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody CrossBorderReportExportRequest request) {
        requireIdempotency(idempotencyKey);
        ensureContext(tenantId, userId);
        CrossBorderReportExportResponse response = reportExportService.exportReport(tenantId, userId, idempotencyKey, request);
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
