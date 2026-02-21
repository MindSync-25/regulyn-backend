package com.regulyn.evidence.controller;

import com.regulyn.evidence.dto.EvidenceBundlePageResponse;
import com.regulyn.evidence.service.EvidenceBundleQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/admin/tenants")
public class AdminEvidenceBundleController {

    private final EvidenceBundleQueryService queryService;

    public AdminEvidenceBundleController(EvidenceBundleQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{tenantId}/evidence/bundles")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'DPO', 'AUDITOR')")
    public ResponseEntity<EvidenceBundlePageResponse> listBundles(
            @PathVariable("tenantId") UUID tenantId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        if (page < 0 || size <= 0 || size > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAGINATION_INVALID");
        }

        EvidenceBundlePageResponse response = queryService.listBundles(tenantId, page, size);
        return ResponseEntity.ok(response);
    }
}
