package io.regulyn.identity.controller;

import io.regulyn.identity.client.EvidenceClient;
import io.regulyn.identity.dto.EvidenceBundlePageResponse;
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
@RequestMapping("/admin/platform/tenants")
public class PlatformEvidenceController {

    private final EvidenceClient evidenceClient;

    public PlatformEvidenceController(EvidenceClient evidenceClient) {
        this.evidenceClient = evidenceClient;
    }

    @GetMapping("/{tenantId}/evidence/bundles")
    @PreAuthorize("hasRole('REGULYN_SUPER_ADMIN')")
    public ResponseEntity<EvidenceBundlePageResponse> listEvidenceBundles(
            @PathVariable("tenantId") UUID tenantId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        if (page < 0 || size <= 0 || size > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAGINATION_INVALID");
        }

        EvidenceBundlePageResponse response = evidenceClient.listBundles(tenantId, page, size);
        if (response == null) {
            EvidenceBundlePageResponse empty = new EvidenceBundlePageResponse();
            empty.setContent(java.util.List.of());
            empty.setNumber(page);
            empty.setSize(size);
            empty.setTotalElements(0);
            empty.setTotalPages(0);
            return ResponseEntity.ok(empty);
        }

        return ResponseEntity.ok(response);
    }
}
