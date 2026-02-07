package com.regulyn.retention.api;

import com.regulyn.retention.model.DeletionExceptionGrantRequest;
import com.regulyn.retention.model.DeletionExceptionResponse;
import com.regulyn.retention.service.DeletionExceptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/deletions")
public class DeletionExceptionController {

    private final DeletionExceptionService exceptionService;

    public DeletionExceptionController(DeletionExceptionService exceptionService) {
        this.exceptionService = exceptionService;
    }

    @PostMapping("/{deletionId}/systems/{executionId}/exceptions/grant")
    public ResponseEntity<DeletionExceptionResponse> grantException(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @PathVariable("deletionId") UUID deletionId,
            @PathVariable("executionId") UUID executionId,
            @RequestBody DeletionExceptionGrantRequest request) {

        DeletionExceptionResponse response = exceptionService.grantException(
                tenantId,
                userId,
                deletionId,
                executionId,
                request
        );
        return ResponseEntity.ok(response);
    }
}