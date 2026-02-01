package io.regulyn.scanner.controller;

import io.regulyn.scanner.dto.PromoteRetentionCandidatesRequest;
import io.regulyn.scanner.dto.PromoteRetentionCandidatesResponse;
import io.regulyn.scanner.service.PromotionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/scanner/runs")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @PostMapping("/{runId}/promote/retention-candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PromoteRetentionCandidatesResponse> promoteRetentionCandidates(
        @PathVariable("runId") UUID runId,
        @Valid @RequestBody PromoteRetentionCandidatesRequest request
    ) {
        PromoteRetentionCandidatesResponse response = promotionService.promoteRetentionCandidates(runId, request);
        return ResponseEntity.ok(response);
    }
}
