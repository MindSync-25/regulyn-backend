package com.regulyn.evidence.controller;

import com.regulyn.evidence.model.*;
import com.regulyn.evidence.service.EvidenceBundleService;
import com.regulyn.evidence.service.ExportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/bundles")
public class BundleController {

    private final EvidenceBundleService bundleService;
    private final ExportService exportService;

    public BundleController(EvidenceBundleService bundleService, ExportService exportService) {
        this.bundleService = bundleService;
        this.exportService = exportService;
    }

    @PostMapping
    public ResponseEntity<CreateBundleResponse> createBundle(@Valid @RequestBody CreateBundleRequest request) {
        CreateBundleResponse response = bundleService.createBundle(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{bundleId}")
    public ResponseEntity<BundleManifestResponse> getBundle(@PathVariable UUID bundleId) {
        BundleManifestResponse response = bundleService.getBundle(bundleId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{bundleId}/export")
    public ResponseEntity<ExportResponse> exportBundle(@PathVariable UUID bundleId) {
        ExportResponse response = bundleService.exportBundle(bundleId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{bundleId}/verify")
    public ResponseEntity<VerifyResponse> verifyBundle(@PathVariable UUID bundleId) {
        VerifyResponse response = bundleService.verifyBundle(bundleId);
        return ResponseEntity.ok(response);
    }
}
