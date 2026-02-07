package com.regulyn.retention.api;

import com.regulyn.retention.model.ManualProofDecisionRequest;
import com.regulyn.retention.model.ManualProofRequest;
import com.regulyn.retention.model.ManualProofTaskResponse;
import com.regulyn.retention.service.ManualProofService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/deletions")
public class DeletionManualProofController {

    private final ManualProofService manualProofService;

    public DeletionManualProofController(ManualProofService manualProofService) {
        this.manualProofService = manualProofService;
    }

    @PostMapping("/{deletionId}/systems/{executionId}/manual-proof/request")
    public ResponseEntity<ManualProofTaskResponse> requestManualProof(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @PathVariable("deletionId") UUID deletionId,
            @PathVariable("executionId") UUID executionId,
            @RequestBody ManualProofRequest request) {

        ManualProofTaskResponse response = manualProofService.requestManualProof(
                tenantId,
                userId,
                deletionId,
                executionId,
                request.getReason()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{deletionId}/systems/{executionId}/manual-proof/submit")
    public ResponseEntity<ManualProofTaskResponse> submitManualProof(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @PathVariable("deletionId") UUID deletionId,
            @PathVariable("executionId") UUID executionId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "notes", required = false) String notes) throws IOException {

        ManualProofTaskResponse response = manualProofService.submitManualProof(
                tenantId,
                userId,
                deletionId,
                executionId,
                file,
                notes
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{deletionId}/systems/{executionId}/manual-proof/decide")
    public ResponseEntity<ManualProofTaskResponse> decideManualProof(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @PathVariable("deletionId") UUID deletionId,
            @PathVariable("executionId") UUID executionId,
            @RequestBody ManualProofDecisionRequest request) {

        ManualProofTaskResponse response = manualProofService.decideManualProof(
                tenantId,
                userId,
                deletionId,
                executionId,
                request
        );
        return ResponseEntity.ok(response);
    }
}