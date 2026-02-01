package com.regulyn.notification.controller;

import com.regulyn.notification.dto.*;
import com.regulyn.notification.service.TemplateManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications/templates")
public class TemplateController {
    
    private final TemplateManagementService templateService;
    
    public TemplateController(TemplateManagementService templateService) {
        this.templateService = templateService;
    }
    
    @PostMapping
    public ResponseEntity<CreateTemplateResponse> createTemplate(@Valid @RequestBody CreateTemplateRequest request) {
        CreateTemplateResponse response = templateService.createTemplate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/{templateId}/versions")
    public ResponseEntity<CreateVersionResponse> createVersion(
        @PathVariable UUID templateId,
        @Valid @RequestBody CreateVersionRequest request
    ) {
        CreateVersionResponse response = templateService.createVersion(templateId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/versions/{versionId}/languages")
    public ResponseEntity<AddLanguageResponse> addLanguage(
        @PathVariable UUID versionId,
        @Valid @RequestBody AddLanguageRequest request
    ) {
        AddLanguageResponse response = templateService.addLanguage(versionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/versions/{versionId}/publish")
    public ResponseEntity<PublishResponse> publishVersion(
        @PathVariable UUID versionId,
        @RequestBody PublishRequest request
    ) {
        PublishResponse response = templateService.publishVersion(versionId, request);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{templateKey}")
    public ResponseEntity<GetActiveTemplateResponse> getActiveTemplate(
        @PathVariable String templateKey,
        @RequestParam(defaultValue = "en") String language
    ) {
        GetActiveTemplateResponse response = templateService.getActiveTemplate(templateKey, language);
        return ResponseEntity.ok(response);
    }
}
