package com.regulyn.nominee.controller;

import com.regulyn.nominee.dto.*;
import com.regulyn.nominee.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/claims")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    @PreAuthorize("hasRole('NOMINEE')")
    public ResponseEntity<ClaimResponse> createClaim(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @Valid @RequestBody CreateClaimRequest request) {
        ClaimResponse response = claimService.createClaim(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClaimResponse> transitionClaim(
            @PathVariable("id") UUID claimId,
            @RequestHeader("X-User-ID") UUID userId,
            @Valid @RequestBody TransitionClaimRequest request) {
        ClaimResponse response = claimService.transitionClaim(claimId, request, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClaimResponse> approveClaim(
            @PathVariable("id") UUID claimId,
            @RequestHeader("X-User-ID") UUID approvedBy,
            @Valid @RequestBody ApproveClaimRequest request) {
        ClaimResponse response = claimService.approveClaim(claimId, request, approvedBy);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClaimResponse> closeClaim(
            @PathVariable("id") UUID claimId,
            @RequestHeader("X-User-ID") UUID closedBy,
            @Valid @RequestBody CloseClaimRequest request) {
        ClaimResponse response = claimService.closeClaim(claimId, request, closedBy);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('NOMINEE', 'ADMIN')")
    public ResponseEntity<ClaimResponse> getClaim(@PathVariable("id") UUID claimId) {
        ClaimResponse response = claimService.getClaim(claimId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/nominee/{nomineeId}")
    @PreAuthorize("hasAnyRole('NOMINEE', 'ADMIN')")
    public ResponseEntity<List<ClaimResponse>> getClaimsByNominee(@PathVariable("nomineeId") UUID nomineeId) {
        List<ClaimResponse> responses = claimService.getClaimsByNominee(nomineeId);
        return ResponseEntity.ok(responses);
    }
}
