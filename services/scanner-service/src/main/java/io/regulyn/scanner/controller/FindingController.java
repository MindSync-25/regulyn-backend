package io.regulyn.scanner.controller;

import io.regulyn.scanner.dto.FindingResponse;
import io.regulyn.scanner.service.FindingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/scanner/runs")
public class FindingController {

    private final FindingService findingService;

    public FindingController(FindingService findingService) {
        this.findingService = findingService;
    }

    @GetMapping("/{runId}/findings")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SCANNER_AGENT')")
    public ResponseEntity<List<FindingResponse>> getRunFindings(@PathVariable("runId") UUID runId) {
        List<FindingResponse> findings = findingService.getRunFindings(runId);
        return ResponseEntity.ok(findings);
    }
}
