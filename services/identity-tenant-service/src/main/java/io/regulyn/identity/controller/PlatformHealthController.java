package io.regulyn.identity.controller;

import io.regulyn.identity.dto.PlatformHealthSummaryDTO;
import io.regulyn.identity.dto.TenantHealthSummaryDTO;
import io.regulyn.identity.service.PlatformHealthService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/platform/health")
@PreAuthorize("hasRole('REGULYN_SUPER_ADMIN')")
public class PlatformHealthController {

    private final PlatformHealthService platformHealthService;

    public PlatformHealthController(PlatformHealthService platformHealthService) {
        this.platformHealthService = platformHealthService;
    }

    @GetMapping("/summary")
    public ResponseEntity<PlatformHealthSummaryDTO> getSummary() {
        return ResponseEntity.ok(platformHealthService.getSummary());
    }

    @GetMapping("/tenants")
    public ResponseEntity<Page<TenantHealthSummaryDTO>> listTenants(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Page<TenantHealthSummaryDTO> result = platformHealthService.listTenantHealth(PageRequest.of(page, size));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/tenants/{tenantId}")
    public ResponseEntity<TenantHealthSummaryDTO> getTenant(@PathVariable("tenantId") UUID tenantId) {
        return ResponseEntity.ok(platformHealthService.getTenantHealth(tenantId));
    }
}
