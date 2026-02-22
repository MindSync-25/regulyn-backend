package io.regulyn.scanner.controller;

import io.regulyn.scanner.dto.CreateScanSourceRequest;
import io.regulyn.scanner.dto.ScanSourceResponse;
import io.regulyn.scanner.service.ScanSourceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/scanner/sources")
public class ScanSourceController {

    private final ScanSourceService scanSourceService;

    public ScanSourceController(ScanSourceService scanSourceService) {
        this.scanSourceService = scanSourceService;
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ScanSourceResponse> createSource(@Valid @RequestBody CreateScanSourceRequest request) {
        ScanSourceResponse response = scanSourceService.createSource(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'DPO', 'REVIEWER', 'OPERATOR', 'AUDITOR')")
    public ResponseEntity<List<ScanSourceResponse>> listSources(
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "type", required = false) String type
    ) {
        List<ScanSourceResponse> sources = scanSourceService.listSources(status, type);
        return ResponseEntity.ok(sources);
    }

    @PostMapping("/{sourceId}/disable")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ScanSourceResponse> disableSource(@PathVariable("sourceId") UUID sourceId) {
        ScanSourceResponse response = scanSourceService.disableSource(sourceId);
        return ResponseEntity.ok(response);
    }
}
