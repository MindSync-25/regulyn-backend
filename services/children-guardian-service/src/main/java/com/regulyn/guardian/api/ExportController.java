package com.regulyn.guardian.api;

import com.regulyn.guardian.dto.CreateExportRequest;
import com.regulyn.guardian.dto.CreateExportResponse;
import com.regulyn.guardian.service.ExportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/exports/guardian-consents")
public class ExportController {
    
    private final ExportService exportService;
    
    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }
    
    @PostMapping
    public ResponseEntity<CreateExportResponse> createExport(@Valid @RequestBody CreateExportRequest request) {
        CreateExportResponse response = exportService.createExport(request);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{exportId}/download")
    public ResponseEntity<byte[]> downloadExport(@PathVariable("exportId") UUID exportId) {
        byte[] data = exportService.downloadExport(exportId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "consent-export-" + exportId + ".zip");
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(data);
    }
}
