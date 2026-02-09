package com.regulyn.vendor.controller;

import com.regulyn.vendor.dto.CreateVendorAccessExportRequest;
import com.regulyn.vendor.dto.CreateVendorAccessExportResponse;
import com.regulyn.vendor.service.VendorAccessExportProcessor;
import com.regulyn.vendor.service.VendorAccessExportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/vendor")
public class VendorAccessExportsController {

    private static final long MAX_RANGE_DAYS = 90;

    private final VendorAccessExportService exportService;
    private final VendorAccessExportProcessor exportProcessor;
    private final String internalAuthToken;

    public VendorAccessExportsController(
        VendorAccessExportService exportService,
        VendorAccessExportProcessor exportProcessor,
        @Value("${internal.auth.token:change-me-in-production}") String internalAuthToken
    ) {
        this.exportService = exportService;
        this.exportProcessor = exportProcessor;
        this.internalAuthToken = internalAuthToken;
    }

    @PostMapping("/access-exports")
    public ResponseEntity<CreateVendorAccessExportResponse> requestExport(
        @RequestHeader("X-Tenant-ID") String tenantIdHeader,
        @RequestHeader(value = "X-User-ID", required = false) String userIdHeader,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody CreateVendorAccessExportRequest request
    ) {
        UUID tenantId = parseUuid(tenantIdHeader, "X-Tenant-ID");
        UUID userId = userIdHeader != null && !userIdHeader.isBlank()
            ? parseUuid(userIdHeader, "X-User-ID")
            : null;

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Idempotency-Key is required");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        if (request.getVendorId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vendorId is required");
        }
        if (request.getFrom() == null || request.getTo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from and to are required");
        }
        if (!request.getTo().isAfter(request.getFrom())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to must be after from");
        }
        if (request.getTo().isAfter(request.getFrom().plusDays(MAX_RANGE_DAYS))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "range must be <= " + MAX_RANGE_DAYS + " days");
        }

        String format = normalizeFormat(request.getFormat());
        request.setFormat(format);

        CreateVendorAccessExportResponse response = exportService.requestExport(
            tenantId,
            userId,
            idempotencyKey,
            request
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/internal/access-exports/{exportId}/process")
    public ResponseEntity<Void> processExport(
        @RequestHeader("X-Internal-Auth") String internalAuth,
        @RequestHeader("X-Tenant-ID") String tenantIdHeader,
        @PathVariable("exportId") UUID exportId
    ) {
        if (internalAuth == null || !internalAuth.equals(internalAuthToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal auth token");
        }
        UUID tenantId = parseUuid(tenantIdHeader, "X-Tenant-ID");
        boolean processed = exportProcessor.processExport(exportId, tenantId);
        if (!processed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "export not found");
        }
        return ResponseEntity.ok().build();
    }

    private UUID parseUuid(String value, String headerName) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, headerName + " must be a valid UUID");
        }
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "format is required");
        }
        String normalizedFormat = format.trim().toUpperCase(Locale.ROOT);
        if (!normalizedFormat.equals("CSV") && !normalizedFormat.equals("JSON")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "format must be CSV or JSON");
        }
        return normalizedFormat;
    }
}
