package com.regulyn.nominee.controller;

import com.regulyn.nominee.dto.RightsGrantResponse;
import com.regulyn.nominee.service.RightsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/rights")
public class RightsController {

    private final RightsService rightsService;

    public RightsController(RightsService rightsService) {
        this.rightsService = rightsService;
    }

    @GetMapping("/principal/{dataPrincipalId}")
    @PreAuthorize("hasAnyRole('DATA_PRINCIPAL', 'ADMIN')")
    public ResponseEntity<List<RightsGrantResponse>> getRightsByPrincipal(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable("dataPrincipalId") UUID dataPrincipalId) {
        List<RightsGrantResponse> responses = rightsService.getRightsByPrincipal(tenantId, dataPrincipalId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/nominee/{nomineeId}")
    @PreAuthorize("hasAnyRole('NOMINEE', 'ADMIN')")
    public ResponseEntity<List<RightsGrantResponse>> getRightsByNominee(@PathVariable("nomineeId") UUID nomineeId) {
        List<RightsGrantResponse> responses = rightsService.getRightsByNominee(nomineeId);
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/{grantId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revokeRights(
            @PathVariable("grantId") UUID grantId,
            @RequestHeader("X-User-ID") UUID userId) {
        rightsService.revokeRights(grantId, userId);
        return ResponseEntity.noContent().build();
    }
}
