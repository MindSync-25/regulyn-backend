package io.regulyn.scanner.controller;

import io.regulyn.scanner.dto.CreateScanRunRequest;
import io.regulyn.scanner.dto.ScanRunResponse;
import io.regulyn.scanner.service.ScanRunService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/scanner/runs")
public class ScanRunController {

    private final ScanRunService scanRunService;

    public ScanRunController(ScanRunService scanRunService) {
        this.scanRunService = scanRunService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SCANNER_AGENT')")
    public ResponseEntity<ScanRunResponse> createRun(@Valid @RequestBody CreateScanRunRequest request) {
        ScanRunResponse response = scanRunService.createRun(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{runId}/execute")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SCANNER_AGENT')")
    public ResponseEntity<ScanRunResponse> executeRun(@PathVariable("runId") UUID runId) {
        ScanRunResponse response = scanRunService.executeRun(runId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{runId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SCANNER_AGENT')")
    public ResponseEntity<ScanRunResponse> getRun(@PathVariable("runId") UUID runId) {
        ScanRunResponse response = scanRunService.getRun(runId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SCANNER_AGENT')")
    public ResponseEntity<Page<ScanRunResponse>> listRuns(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID sourceId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ScanRunResponse> runs = scanRunService.listRuns(status, sourceId, pageable);
        return ResponseEntity.ok(runs);
    }
}
