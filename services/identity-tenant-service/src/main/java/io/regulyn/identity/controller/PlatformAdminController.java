package io.regulyn.identity.controller;

import io.regulyn.identity.dto.TenantDetailDTO;
import io.regulyn.identity.dto.TenantResponse;
import io.regulyn.identity.dto.TenantSummaryDTO;
import io.regulyn.identity.service.PlatformAdminService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Platform admin controller for cross-tenant operations.
 * All endpoints require REGULYN_SUPER_ADMIN role.
 * No X-Tenant-Id context required.
 */
@RestController
@RequestMapping("/admin/platform")
@PreAuthorize("hasRole('REGULYN_SUPER_ADMIN')")
public class PlatformAdminController {

    private final PlatformAdminService platformAdminService;

    public PlatformAdminController(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @GetMapping("/tenants")
    public ResponseEntity<Page<TenantSummaryDTO>> listTenants(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) String status) {
        Page<TenantSummaryDTO> tenants = platformAdminService.listTenants(page, size, status);
        return ResponseEntity.ok(tenants);
    }

    @GetMapping("/tenants/{tenantId}")
    public ResponseEntity<TenantDetailDTO> getTenant(@PathVariable("tenantId") UUID tenantId) {
        TenantDetailDTO tenant = platformAdminService.getTenantDetail(tenantId);
        return ResponseEntity.ok(tenant);
    }

    @PostMapping("/tenants/{tenantId}/activate")
    public ResponseEntity<TenantResponse> activateTenant(@PathVariable("tenantId") UUID tenantId) {
        TenantResponse response = platformAdminService.activateTenant(tenantId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tenants/{tenantId}/suspend")
    public ResponseEntity<TenantResponse> suspendTenant(@PathVariable("tenantId") UUID tenantId) {
        TenantResponse response = platformAdminService.suspendTenant(tenantId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tenants/{tenantId}/resume")
    public ResponseEntity<TenantResponse> resumeTenant(@PathVariable("tenantId") UUID tenantId) {
        TenantResponse response = platformAdminService.resumeTenant(tenantId);
        return ResponseEntity.ok(response);
    }
}
