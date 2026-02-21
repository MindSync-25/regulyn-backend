package io.regulyn.identity.controller;

import io.regulyn.identity.dto.SupportAuditRequest;
import io.regulyn.identity.service.SupportAuditService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/platform/support")
public class SupportAuditController {

    private final SupportAuditService supportAuditService;

    public SupportAuditController(SupportAuditService supportAuditService) {
        this.supportAuditService = supportAuditService;
    }

    @PostMapping("/audit")
    @PreAuthorize("hasRole('REGULYN_SUPER_ADMIN')")
    public ResponseEntity<Void> recordAudit(@Valid @RequestBody SupportAuditRequest request) {
        supportAuditService.recordSupportAudit(request);
        return ResponseEntity.noContent().build();
    }
}
