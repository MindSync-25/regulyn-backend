package com.regulyn.evidence.controller;

import com.regulyn.evidence.model.*;
import com.regulyn.evidence.service.EvidenceBundleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/bundles")
public class BundleController {

    private final EvidenceBundleService bundleService;

    public BundleController(EvidenceBundleService bundleService) {
        this.bundleService = bundleService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'DPO', 'OPERATOR')")
    public ResponseEntity<CreateBundleResponse> createBundle(@Valid @RequestBody CreateBundleRequest request) {
        CreateBundleResponse response = bundleService.createBundle(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{bundleId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'DPO', 'AUDITOR')")
    public ResponseEntity<BundleManifestResponse> getBundle(@PathVariable("bundleId") UUID bundleId) {
        BundleManifestResponse response = bundleService.getBundle(bundleId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{bundleId}/export")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'DPO', 'AUDITOR')")
    public ResponseEntity<ExportResponse> exportBundle(@PathVariable("bundleId") UUID bundleId) {
        ExportResponse response = bundleService.exportBundle(bundleId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{bundleId}/verify")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'DPO', 'AUDITOR')")
    public ResponseEntity<VerifyResponse> verifyBundle(@PathVariable("bundleId") UUID bundleId) {
        VerifyResponse response = bundleService.verifyBundle(bundleId);
        return ResponseEntity.ok(response);
    }
}
