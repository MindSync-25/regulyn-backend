package com.regulyn.evidence.controller;

import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.evidence.entity.EvidenceExport;
import com.regulyn.evidence.repository.EvidenceExportRepository;
import com.regulyn.evidence.service.ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/exports")
public class ExportController {

    private final EvidenceExportRepository exportRepository;
    private final ExportService exportService;

    public ExportController(EvidenceExportRepository exportRepository, ExportService exportService) {
        this.exportRepository = exportRepository;
        this.exportService = exportService;
    }

    @GetMapping("/{exportId}/download")
    public ResponseEntity<byte[]> downloadExport(@PathVariable UUID exportId) {
        UUID tenantId = TenantContextHolder.getContext().getTenantId();

        EvidenceExport export = exportRepository.findByExportIdAndTenantId(exportId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Export not found"));

        if (!"READY".equals(export.getStatus())) {
            throw new IllegalStateException("Export not ready: " + export.getStatus());
        }

        try {
            byte[] zipBytes = exportService.readExport(tenantId, exportId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", 
                    "evidence-bundle-" + export.getBundleId() + ".zip");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(zipBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to download export", e);
        }
    }
}
