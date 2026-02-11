package com.regulyn.nominee.controller;

import com.regulyn.nominee.dto.NomineeDocumentRefRequest;
import com.regulyn.nominee.dto.NomineeDocumentResponse;
import com.regulyn.nominee.service.NomineeDocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/nominees/{nomineeId}/documents")
public class NomineeDocumentController {

    private final NomineeDocumentService nomineeDocumentService;

    public NomineeDocumentController(NomineeDocumentService nomineeDocumentService) {
        this.nomineeDocumentService = nomineeDocumentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('NOMINEE', 'ADMIN')")
    public ResponseEntity<NomineeDocumentResponse> uploadDocument(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader(value = "X-User-ID", required = false) UUID userId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("nomineeId") UUID nomineeId,
            @RequestPart("file") MultipartFile file,
            @RequestPart("verificationStep") String verificationStep,
            @RequestPart(value = "claimId", required = false) String claimId,
            @RequestPart(value = "notes", required = false) String notes) {
        UUID claimUuid = null;
        if (claimId != null && !claimId.isBlank()) {
            try {
                claimUuid = UUID.fromString(claimId.trim());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "claimId must be a valid UUID");
            }
        }
        NomineeDocumentResponse response = nomineeDocumentService.uploadDocument(
                tenantId,
                nomineeId,
                userId,
                file,
                verificationStep,
                claimUuid,
                notes,
                idempotencyKey
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/ref", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('NOMINEE', 'ADMIN')")
    public ResponseEntity<NomineeDocumentResponse> submitDocumentReference(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader(value = "X-User-ID", required = false) UUID userId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("nomineeId") UUID nomineeId,
            @Valid @RequestBody NomineeDocumentRefRequest request) {
        NomineeDocumentResponse response = nomineeDocumentService.registerDocumentRef(
                tenantId,
                nomineeId,
                userId,
                request,
                idempotencyKey
        );
        return ResponseEntity.ok(response);
    }
}
