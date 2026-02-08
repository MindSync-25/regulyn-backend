package com.regulyn.consent.api;

import com.regulyn.consent.model.ActivePurposeVersionResponse;
import com.regulyn.consent.model.ConsentValidityResponse;
import com.regulyn.consent.service.ConsentEvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v2/consent")
public class ConsentEvaluationControllerV2 {

    private final ConsentEvaluationService consentEvaluationService;

    public ConsentEvaluationControllerV2(ConsentEvaluationService consentEvaluationService) {
        this.consentEvaluationService = consentEvaluationService;
    }

    @GetMapping("/consents/valid")
    public ResponseEntity<ConsentValidityResponse> validateConsent(
            @RequestParam UUID dataPrincipalId,
            @RequestParam String purpose,
            @RequestParam UUID requiredPurposeVersionId) {
        return ResponseEntity.ok(
            consentEvaluationService.validateConsent(dataPrincipalId, purpose, requiredPurposeVersionId)
        );
    }

    @GetMapping("/notices/{noticeId}/purposes/{purposeKey}/active-version")
    public ResponseEntity<ActivePurposeVersionResponse> getActivePurposeVersion(
            @PathVariable UUID noticeId,
            @PathVariable String purposeKey) {
        ActivePurposeVersionResponse response = consentEvaluationService.getActivePurposeVersion(noticeId, purposeKey);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }
}
