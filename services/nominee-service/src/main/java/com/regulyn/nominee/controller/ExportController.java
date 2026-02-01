package com.regulyn.nominee.controller;

import com.regulyn.nominee.dto.ExportNomineeRequest;
import com.regulyn.nominee.dto.ExportResponse;
import com.regulyn.nominee.service.ExportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/exports")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping("/nominee")
    @PreAuthorize("hasAnyRole('DATA_PRINCIPAL', 'ADMIN')")
    public ResponseEntity<ExportResponse> requestExport(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @Valid @RequestBody ExportNomineeRequest request) {
        ExportResponse response = exportService.requestExport(tenantId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{exportId}")
    @PreAuthorize("hasAnyRole('DATA_PRINCIPAL', 'ADMIN')")
    public ResponseEntity<ExportResponse> getExport(@PathVariable("exportId") UUID exportId) {
        ExportResponse response = exportService.getExport(exportId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/nominee/{nomineeId}")
    @PreAuthorize("hasAnyRole('DATA_PRINCIPAL', 'ADMIN')")
    public ResponseEntity<List<ExportResponse>> getExportsByNominee(@PathVariable("nomineeId") UUID nomineeId) {
        List<ExportResponse> responses = exportService.getExportsByNominee(nomineeId);
        return ResponseEntity.ok(responses);
    }
}
