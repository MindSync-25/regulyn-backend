package io.regulyn.scanner.controller;

import io.regulyn.scanner.dto.CreateScanExportRequest;
import io.regulyn.scanner.dto.ScanExportResponse;
import io.regulyn.scanner.service.ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/scanner")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping("/exports/scans")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'DPO', 'AUDITOR')")
    public ResponseEntity<ScanExportResponse> createExport(@RequestBody CreateScanExportRequest request) {
        ScanExportResponse response = exportService.createExport(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/exports/{exportId}/download")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'DPO', 'AUDITOR')")
    public ResponseEntity<byte[]> downloadExport(@PathVariable("exportId") UUID exportId) {
        byte[] data = exportService.downloadExport(exportId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "scan-export-" + exportId + ".zip");

        return ResponseEntity.ok()
            .headers(headers)
            .body(data);
    }
}
