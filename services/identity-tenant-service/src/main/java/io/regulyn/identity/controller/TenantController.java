package io.regulyn.identity.controller;

import io.regulyn.identity.dto.*;
import io.regulyn.identity.service.TenantLifecycleService;
import io.regulyn.identity.service.TenantPlanLimitsService;
import io.regulyn.identity.service.TenantFeatureFlagService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/tenants")
public class TenantController {

    private final TenantLifecycleService tenantLifecycleService;
    private final TenantPlanLimitsService tenantPlanLimitsService;
    private final TenantFeatureFlagService tenantFeatureFlagService;

    public TenantController(TenantLifecycleService tenantLifecycleService,
                            TenantPlanLimitsService tenantPlanLimitsService,
                            TenantFeatureFlagService tenantFeatureFlagService) {
        this.tenantLifecycleService = tenantLifecycleService;
        this.tenantPlanLimitsService = tenantPlanLimitsService;
        this.tenantFeatureFlagService = tenantFeatureFlagService;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(@RequestBody CreateTenantRequest request) {
        return ResponseEntity.ok(tenantLifecycleService.createTenant(request));
    }

    @PostMapping("/{tenantId}/bootstrap-admin")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<BootstrapAdminResponse> bootstrapAdmin(@PathVariable("tenantId") UUID tenantId,
                                                                 @RequestBody BootstrapAdminRequest request) {
        return ResponseEntity.ok(tenantLifecycleService.bootstrapAdmin(tenantId, request));
    }

    @PostMapping("/{tenantId}/activate")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<TenantResponse> activate(@PathVariable("tenantId") UUID tenantId) {
        return ResponseEntity.ok(tenantLifecycleService.activate(tenantId));
    }

    @PostMapping(value = "/{tenantId}/suspend", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<TenantResponse> suspend(@PathVariable("tenantId") UUID tenantId,
                                                  @RequestBody(required = false) SuspendTenantRequest request) {
        return ResponseEntity.ok(tenantLifecycleService.suspend(tenantId, request));
    }

    @PostMapping(value = "/{tenantId}/suspend", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<TenantResponse> suspendForm(@PathVariable("tenantId") UUID tenantId) {
        return ResponseEntity.ok(tenantLifecycleService.suspend(tenantId, null));
    }

    @PostMapping("/{tenantId}/resume")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<TenantResponse> resume(@PathVariable("tenantId") UUID tenantId) {
        return ResponseEntity.ok(tenantLifecycleService.resume(tenantId));
    }

    @PostMapping("/{tenantId}/delete-request")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<TenantResponse> requestDelete(@PathVariable("tenantId") UUID tenantId,
                                                        @RequestBody(required = false) DeleteTenantRequest request) {
        return ResponseEntity.ok(tenantLifecycleService.requestDelete(tenantId, request));
    }

    @DeleteMapping("/{tenantId}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<TenantResponse> delete(@PathVariable("tenantId") UUID tenantId,
                                                 @RequestBody(required = false) DeleteTenantRequest request) {
        return ResponseEntity.ok(tenantLifecycleService.delete(tenantId, request));
    }

    @GetMapping("/plan-limits")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<TenantPlanLimitsResponse> getPlanLimits() {
        return ResponseEntity.ok(tenantPlanLimitsService.getPlanLimits());
    }

    @PutMapping("/plan-limits")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<TenantPlanLimitsResponse> updatePlanLimits(@RequestBody TenantPlanLimitsRequest request) {
        return ResponseEntity.ok(tenantPlanLimitsService.updatePlanLimits(request));
    }

    @GetMapping("/feature-flags")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<java.util.List<TenantFeatureFlagResponse>> getFeatureFlags() {
        return ResponseEntity.ok(tenantFeatureFlagService.getFeatureFlags());
    }

    @PutMapping("/feature-flags/{flagKey}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<TenantFeatureFlagResponse> upsertFeatureFlag(@PathVariable("flagKey") String flagKey,
                                                                       @RequestBody TenantFeatureFlagRequest request) {
        return ResponseEntity.ok(tenantFeatureFlagService.upsertFeatureFlag(flagKey, request));
    }
}
