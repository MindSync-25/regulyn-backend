package com.regulyn.guardian.api;

import com.regulyn.guardian.dto.*;
import com.regulyn.guardian.service.ConsentService;
import com.regulyn.guardian.service.EsignRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/consents")
public class ConsentController {
    
    private final ConsentService consentService;
    private final EsignRequestService esignRequestService;
    
    public ConsentController(ConsentService consentService, EsignRequestService esignRequestService) {
        this.consentService = consentService;
        this.esignRequestService = esignRequestService;
    }
    
    @PostMapping
    public ResponseEntity<CreateConsentResponse> createConsent(@Valid @RequestBody CreateConsentRequest request) {
        CreateConsentResponse response = consentService.createConsent(request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{consentId}/approve")
    public ResponseEntity<ApproveConsentResponse> approveConsent(
            @PathVariable("consentId") UUID consentId,
            @Valid @RequestBody ApproveConsentRequest request) {
        ApproveConsentResponse response = consentService.approveConsent(consentId, request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{consentId}/revoke")
    public ResponseEntity<RevokeConsentResponse> revokeConsent(
            @PathVariable("consentId") UUID consentId,
            @RequestBody RevokeConsentRequest request) {
        RevokeConsentResponse response = consentService.revokeConsent(consentId, request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{consentId}/close")
    public ResponseEntity<CloseConsentResponse> closeConsent(
            @PathVariable("consentId") UUID consentId,
            @RequestBody CloseConsentRequest request) {
        CloseConsentResponse response = consentService.closeConsent(consentId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{consentId}/esign-requests")
    public ResponseEntity<CreateEsignRequestResponse> createEsignRequest(
            @PathVariable("consentId") UUID consentId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateEsignRequestRequest request) {
        CreateEsignRequestResponse response = esignRequestService.createEsignRequest(consentId, request, idempotencyKey);
        return ResponseEntity.ok(response);
    }
}
