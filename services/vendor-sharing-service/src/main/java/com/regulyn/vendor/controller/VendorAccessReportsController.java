package com.regulyn.vendor.controller;

import com.regulyn.vendor.dto.VendorAccessEventsPageResponse;
import com.regulyn.vendor.dto.VendorAccessSummaryResponseDto;
import com.regulyn.vendor.service.VendorAccessReportsService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/vendor")
public class VendorAccessReportsController {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private static final long MAX_RANGE_DAYS = 90;

    private final VendorAccessReportsService reportsService;

    public VendorAccessReportsController(VendorAccessReportsService reportsService) {
        this.reportsService = reportsService;
    }

    @GetMapping("/access-events")
    public ResponseEntity<VendorAccessEventsPageResponse> queryAccessEvents(
        @RequestHeader("X-Tenant-ID") String tenantIdHeader,
        @RequestParam(value = "vendorId", required = false) UUID vendorId,
        @RequestParam(value = "subjectRef", required = false) String subjectRef,
        @RequestParam(value = "from") OffsetDateTime from,
        @RequestParam(value = "to") OffsetDateTime to,
        @RequestParam(value = "systemName", required = false) String systemName,
        @RequestParam(value = "accessType", required = false) String accessType,
        @RequestParam(value = "result", required = false) String result,
        @RequestParam(value = "correlationId", required = false) String correlationId,
        @RequestParam(value = "includeRawRef", required = false, defaultValue = "false") boolean includeRawRef,
        @RequestParam(value = "page", required = false, defaultValue = "0") int page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "sort", required = false, defaultValue = "accessedAt,desc") String sort
    ) {
        UUID tenantId = parseUuid(tenantIdHeader, "X-Tenant-ID");

        if ((vendorId == null) && (subjectRef == null || subjectRef.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vendorId or subjectRef is required");
        }

        validateRange(from, to);

        int resolvedSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (resolvedSize <= 0 || resolvedSize > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0");
        }

        Sort sortSpec = parseSort(sort);

        VendorAccessEventsPageResponse response = reportsService.queryEvents(
            tenantId,
            vendorId,
            blankToNull(subjectRef),
            from,
            to,
            blankToNull(systemName),
            blankToNull(accessType),
            blankToNull(result),
            blankToNull(correlationId),
            includeRawRef,
            page,
            resolvedSize,
            sortSpec
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/access-events/vendors/{vendorId}/summary")
    public ResponseEntity<VendorAccessSummaryResponseDto> summaryByVendor(
        @RequestHeader("X-Tenant-ID") String tenantIdHeader,
        @PathVariable("vendorId") UUID vendorId,
        @RequestParam(value = "from") OffsetDateTime from,
        @RequestParam(value = "to") OffsetDateTime to,
        @RequestParam(value = "systemName", required = false) String systemName,
        @RequestParam(value = "accessType", required = false) String accessType,
        @RequestParam(value = "result", required = false) String result
    ) {
        UUID tenantId = parseUuid(tenantIdHeader, "X-Tenant-ID");
        validateRange(from, to);

        VendorAccessSummaryResponseDto response = reportsService.summarizeVendorAccess(
            tenantId,
            vendorId,
            from,
            to,
            blankToNull(systemName),
            blankToNull(accessType),
            blankToNull(result)
        );

        return ResponseEntity.ok(response);
    }

    private void validateRange(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from and to are required");
        }
        if (!to.isAfter(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to must be after from");
        }
        if (to.isAfter(from.plusDays(MAX_RANGE_DAYS))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "range must be <= " + MAX_RANGE_DAYS + " days");
        }
    }

    private UUID parseUuid(String value, String headerName) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, headerName + " must be a valid UUID");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Order.desc("accessedAt"));
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        String direction = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "desc";

        if (!property.equals("accessedAt") && !property.equals("receivedAt")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sort must be accessedAt or receivedAt");
        }

        Sort.Direction sortDirection = "asc".equals(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(new Sort.Order(sortDirection, property));
    }
}
