package com.regulyn.consent.api;

import com.regulyn.consent.model.*;
import com.regulyn.consent.service.NoticeManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2/consent")
public class NoticeControllerV2 {
    
    private final NoticeManagementService noticeManagementService;
    
    public NoticeControllerV2(NoticeManagementService noticeManagementService) {
        this.noticeManagementService = noticeManagementService;
    }
    
    @PostMapping("/notices")
    public ResponseEntity<CreateNoticeResponse> createNotice(@Valid @RequestBody CreateNoticeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(noticeManagementService.createNotice(request));
    }
    
    @PostMapping("/notices/{noticeId}/versions")
    public ResponseEntity<CreateVersionResponse> createVersion(
            @PathVariable UUID noticeId,
            @Valid @RequestBody CreateVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(noticeManagementService.createVersion(noticeId, request));
    }
    
    @PostMapping("/notices/{noticeId}/versions/{versionId}/languages")
    public ResponseEntity<AddLanguageResponse> addLanguage(
            @PathVariable UUID noticeId,
            @PathVariable UUID versionId,
            @Valid @RequestBody AddLanguageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(noticeManagementService.addLanguage(noticeId, versionId, request));
    }
    
    @PostMapping("/notices/{noticeId}/versions/{versionId}/publish")
    public ResponseEntity<PublishVersionResponse> publishVersion(
            @PathVariable UUID noticeId,
            @PathVariable UUID versionId) {
        return ResponseEntity.ok(noticeManagementService.publishVersion(noticeId, versionId));
    }
    
    @GetMapping("/notices/active")
    public ResponseEntity<ActiveNoticeResponse> getActiveNotice(
            @RequestParam String purpose,
            @RequestParam(defaultValue = "en") String language) {
        return ResponseEntity.ok(noticeManagementService.getActiveNotice(purpose, language));
    }
    
    @PostMapping("/consents")
    public ResponseEntity<GrantConsentResponse> grantConsent(@Valid @RequestBody GrantConsentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(noticeManagementService.grantConsent(request));
    }
    
    @PostMapping("/consents/{receiptId}/withdraw")
    public ResponseEntity<WithdrawConsentResponse> withdrawConsent(
            @PathVariable UUID receiptId,
            @Valid @RequestBody WithdrawConsentRequest request) {
        return ResponseEntity.ok(noticeManagementService.withdrawConsent(receiptId, request));
    }
    
    @GetMapping("/consents")
    public ResponseEntity<List<ConsentReceiptDto>> listConsents(
            @RequestParam UUID dataPrincipalId,
            @RequestParam(required = false) String purpose) {
        return ResponseEntity.ok(noticeManagementService.listConsents(dataPrincipalId, purpose));
    }
}
