package com.regulyn.guardian.api;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.guardian.dto.*;
import com.regulyn.guardian.service.EvidenceExportService;
import com.regulyn.guardian.service.MajorityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/children")
public class ChildMajorityController {

    private final MajorityService majorityService;
    private final EvidenceExportService evidenceExportService;

    public ChildMajorityController(MajorityService majorityService, EvidenceExportService evidenceExportService) {
        this.majorityService = majorityService;
        this.evidenceExportService = evidenceExportService;
    }

    @PostMapping("/{childId}/majority-check")
    public ResponseEntity<MajorityCheckResponse> majorityCheck(
            @PathVariable("childId") UUID childId,
            @RequestBody(required = false) MajorityCheckRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();

        MajorityService.MajorityTransitionResponse result = majorityService.processMajorityTransition(
                tenantId,
                childId,
                request != null ? request.evaluationDate() : null
        );

        return ResponseEntity.ok(new MajorityCheckResponse(
                result.childId(),
                result.majorityDate(),
                result.reached(),
                result.transitionStatus(),
                result.resolvedFrom(),
                result.error()
        ));
    }

    @PostMapping("/{childId}/adult-consent-link")
    public ResponseEntity<AdultConsentLinkResponse> linkAdultConsent(
            @PathVariable("childId") UUID childId,
            @Valid @RequestBody AdultConsentLinkRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();

        MajorityService.AdultConsentLinkResponse response = majorityService.linkAdultConsent(
                tenantId,
                childId,
                request.consentReceiptRef(),
                request.consentReceiptSha256()
        );

        return ResponseEntity.ok(new AdultConsentLinkResponse(
                response.childId(),
                response.consentReceiptRef(),
                response.recordedAt()
        ));
    }

    @PostMapping("/{childId}/evidence-exports")
    public ResponseEntity<EvidenceExportResponse> createEvidenceExport(
            @PathVariable("childId") UUID childId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody EvidenceExportRequest request) {
        EvidenceExportService.EvidenceExportResponse response = evidenceExportService.createChildEvidenceExport(
                childId,
                request.exportScope(),
                idempotencyKey
        );

        return ResponseEntity.ok(new EvidenceExportResponse(
                response.exportId(),
                response.status(),
                response.bundleRef(),
                response.bundleSha256()
        ));
    }
}
