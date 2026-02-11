package com.regulyn.nominee.controller;

import com.regulyn.nominee.dto.RegisterNomineeRequest;
import com.regulyn.nominee.dto.NomineeResponse;
import com.regulyn.nominee.dto.VerifyNomineeRequest;
import com.regulyn.nominee.dto.VerifyNomineeRejectRequest;
import com.regulyn.nominee.dto.NomineeVerificationExceptionRequest;
import com.regulyn.nominee.dto.NomineeDocumentResponse;
import com.regulyn.nominee.service.NomineeService;
import com.regulyn.nominee.service.NomineeDocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/nominees")
public class NomineeController {

    private final NomineeService nomineeService;
    private final NomineeDocumentService nomineeDocumentService;

    public NomineeController(NomineeService nomineeService,
                             NomineeDocumentService nomineeDocumentService) {
        this.nomineeService = nomineeService;
        this.nomineeDocumentService = nomineeDocumentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('DATA_PRINCIPAL')")
    public ResponseEntity<NomineeResponse> registerNominee(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID dataPrincipalId,
            @Valid @RequestBody RegisterNomineeRequest request) {
        NomineeResponse response = nomineeService.registerNominee(tenantId, dataPrincipalId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NomineeResponse> verifyNominee(
            @PathVariable("id") UUID nomineeId,
            @RequestHeader(value = "X-User-ID", required = false) UUID userId,
            @Valid @RequestBody VerifyNomineeRequest request) {
        NomineeResponse response = nomineeService.verifyNominee(nomineeId, userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/verify/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NomineeResponse> rejectNominee(
            @PathVariable("id") UUID nomineeId,
            @RequestHeader("X-User-ID") UUID userId,
            @Valid @RequestBody VerifyNomineeRejectRequest request) {
        NomineeResponse response = nomineeService.rejectNominee(nomineeId, userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/verify/exception")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NomineeDocumentResponse> recordVerificationException(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable("id") UUID nomineeId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody NomineeVerificationExceptionRequest request) {
        NomineeDocumentResponse response = nomineeDocumentService.registerVerificationException(
                tenantId,
                nomineeId,
                request.getApprovedBy(),
                request.getExceptionReason(),
                request.getNotes(),
                request.getArtifactRef(),
                request.getSha256Hash(),
                request.getFilename(),
                request.getContentType(),
                request.getSizeBytes(),
                request.getClaimId(),
                idempotencyKey
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> disableNominee(
            @PathVariable("id") UUID nomineeId,
            @RequestHeader("X-User-ID") UUID userId) {
        nomineeService.disableNominee(nomineeId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DATA_PRINCIPAL', 'ADMIN')")
    public ResponseEntity<NomineeResponse> getNominee(@PathVariable("id") UUID nomineeId) {
        NomineeResponse response = nomineeService.getNominee(nomineeId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('DATA_PRINCIPAL')")
    public ResponseEntity<List<NomineeResponse>> getNomineesByPrincipal(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID dataPrincipalId) {
        List<NomineeResponse> responses = nomineeService.getNomineesByPrincipal(tenantId, dataPrincipalId);
        return ResponseEntity.ok(responses);
    }
}
