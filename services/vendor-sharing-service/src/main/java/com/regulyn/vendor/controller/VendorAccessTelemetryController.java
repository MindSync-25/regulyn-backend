package com.regulyn.vendor.controller;

import com.regulyn.vendor.dto.IngestVendorAccessEventsRequest;
import com.regulyn.vendor.dto.IngestVendorAccessEventsResponse;
import com.regulyn.vendor.service.VendorAccessTelemetryIngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/vendor")
public class VendorAccessTelemetryController {

    private final VendorAccessTelemetryIngestionService ingestionService;

    public VendorAccessTelemetryController(VendorAccessTelemetryIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/access-events/ingest")
    public ResponseEntity<IngestVendorAccessEventsResponse> ingestAccessEvents(
        @RequestHeader("X-Tenant-ID") String tenantIdHeader,
        @RequestHeader(value = "X-User-ID", required = false) String userIdHeader,
        @RequestHeader(value = "X-Request-ID", required = false) String requestId,
        @RequestBody IngestVendorAccessEventsRequest request
    ) {
        if (request == null || request.getEvents() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "events is required");
        }

        UUID tenantId = parseUuid(tenantIdHeader, "X-Tenant-ID");
        UUID userId = userIdHeader != null && !userIdHeader.isBlank()
            ? parseUuid(userIdHeader, "X-User-ID")
            : null;

        IngestVendorAccessEventsResponse response = ingestionService.ingestBatch(tenantId, userId, request.getEvents());
        return ResponseEntity.ok(response);
    }

    private UUID parseUuid(String value, String headerName) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, headerName + " must be a valid UUID");
        }
    }
}
