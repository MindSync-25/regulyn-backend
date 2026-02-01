package io.regulyn.connector.controller;

import io.regulyn.connector.dto.CreateExportRequest;
import io.regulyn.connector.dto.ExportResponse;
import io.regulyn.connector.service.ExportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/connector")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping("/exports/jobs")
    @PreAuthorize("hasRole('CONNECTOR_AGENT') or hasRole('ADMIN')")
    public ResponseEntity<ExportResponse> createExport(@Valid @RequestBody CreateExportRequest request) {
        ExportResponse response = exportService.createExport(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/exports/{exportId}/download")
    @PreAuthorize("hasRole('CONNECTOR_AGENT') or hasRole('ADMIN')")
    public ResponseEntity<byte[]> downloadExport(@PathVariable UUID exportId) {
        byte[] exportData = exportService.downloadExport(exportId);
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"connector-export-" + exportId + ".zip\"")
                .body(exportData);
    }
}
