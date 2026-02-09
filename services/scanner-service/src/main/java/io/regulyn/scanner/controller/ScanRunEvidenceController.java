package io.regulyn.scanner.controller;

import io.regulyn.scanner.dto.RunEvidenceBundleResponse;
import io.regulyn.scanner.service.ScanRunEvidenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/scanner/runs")
public class ScanRunEvidenceController {

    private final ScanRunEvidenceService scanRunEvidenceService;

    public ScanRunEvidenceController(ScanRunEvidenceService scanRunEvidenceService) {
        this.scanRunEvidenceService = scanRunEvidenceService;
    }

    @PostMapping("/{runId}/evidence/bundle")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SCANNER_AGENT')")
    public ResponseEntity<RunEvidenceBundleResponse> createEvidenceBundle(
        @PathVariable("runId") UUID runId,
        @RequestHeader("X-User-ID") UUID userId,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey
    ) {
        RunEvidenceBundleResponse response = scanRunEvidenceService.createRunEvidenceBundle(runId, userId, idempotencyKey);
        return ResponseEntity.ok(response);
    }
}
