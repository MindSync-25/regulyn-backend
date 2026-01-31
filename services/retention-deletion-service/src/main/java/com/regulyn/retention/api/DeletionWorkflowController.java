package com.regulyn.retention.api;

import com.regulyn.retention.integration.EvidenceServiceClient;
import com.regulyn.retention.model.*;
import com.regulyn.retention.service.DeletionWorkflowService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/deletions")
public class DeletionWorkflowController {

    private final DeletionWorkflowService deletionWorkflowService;

    public DeletionWorkflowController(DeletionWorkflowService deletionWorkflowService) {
        this.deletionWorkflowService = deletionWorkflowService;
    }

    @PostMapping
    public ResponseEntity<CreateDeletionResponse> createDeletion(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateDeletionRequest request) {

        CreateDeletionResponse response = deletionWorkflowService.createDeletion(tenantId, userId, request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{deletionId}/assign")
    public ResponseEntity<AssignDeletionResponse> assignDeletion(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @PathVariable("deletionId") UUID deletionId,
            @Valid @RequestBody AssignDeletionRequest request) {

        AssignDeletionResponse response = deletionWorkflowService.assignDeletion(tenantId, userId, deletionId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{deletionId}/approve")
    public ResponseEntity<ApproveDeletionResponse> approveDeletion(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @PathVariable("deletionId") UUID deletionId,
            @Valid @RequestBody ApproveDeletionRequest request) {

        ApproveDeletionResponse response = deletionWorkflowService.approveDeletion(tenantId, userId, deletionId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{deletionId}/transition")
    public ResponseEntity<TransitionDeletionResponse> transitionStatus(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @PathVariable("deletionId") UUID deletionId,
            @Valid @RequestBody TransitionDeletionRequest request) {

        TransitionDeletionResponse response = deletionWorkflowService.transitionStatus(tenantId, userId, deletionId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{deletionId}/proofs")
    public ResponseEntity<UploadProofResponse> uploadProof(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @PathVariable("deletionId") UUID deletionId,
            @RequestParam("file") MultipartFile file) throws IOException {

        UploadProofResponse response = deletionWorkflowService.uploadProof(tenantId, userId, deletionId, file);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{deletionId}/close")
    public ResponseEntity<?> closeDeletion(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @PathVariable("deletionId") UUID deletionId,
            @RequestBody CloseDeletionRequest request) {

        try {
            CloseDeletionResponse response = deletionWorkflowService.closeDeletion(tenantId, userId, deletionId, request);
            return ResponseEntity.ok(response);
        } catch (EvidenceServiceClient.EvidenceServiceUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Evidence service unavailable - cannot close deletion"));
        }
    }

    @GetMapping("/{deletionId}")
    public ResponseEntity<DeletionDetailResponse> getDeletion(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable("deletionId") UUID deletionId) {

        DeletionDetailResponse response = deletionWorkflowService.getDeletion(tenantId, deletionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<DeletionDetailResponse>> searchDeletions(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam(required = false) String status,
            Pageable pageable) {

        Page<DeletionDetailResponse> results = deletionWorkflowService.searchDeletions(tenantId, status, pageable);
        return ResponseEntity.ok(results);
    }

    private static class Map {
        public static java.util.Map<String, String> of(String key, String value) {
            return java.util.Map.of(key, value);
        }
    }
}
