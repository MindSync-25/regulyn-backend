package io.regulyn.identity.controller;

import io.regulyn.identity.dto.PlatformAuditPageResponse;
import io.regulyn.identity.service.PlatformAuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/platform/tenants")
public class PlatformTenantAuditController {

    private final PlatformAuditService platformAuditService;

    public PlatformTenantAuditController(PlatformAuditService platformAuditService) {
        this.platformAuditService = platformAuditService;
    }

    @GetMapping("/{tenantId}/audit/events")
    @PreAuthorize("hasRole('REGULYN_SUPER_ADMIN')")
    public ResponseEntity<PlatformAuditPageResponse> getTenantAuditTimeline(
            @PathVariable("tenantId") String tenantId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        PlatformAuditPageResponse response = platformAuditService.query(
                tenantId,
                null,
                null,
                null,
                null,
                null,
                page,
                size
        );
        return ResponseEntity.ok(response);
    }
}
