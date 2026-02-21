package io.regulyn.identity.controller;

import io.regulyn.identity.dto.PlatformAuditPageResponse;
import io.regulyn.identity.service.PlatformAuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/platform/audit")
@PreAuthorize("hasRole('REGULYN_SUPER_ADMIN')")
public class PlatformAuditController {

    private final PlatformAuditService platformAuditService;

    public PlatformAuditController(PlatformAuditService platformAuditService) {
        this.platformAuditService = platformAuditService;
    }

    @GetMapping("/events")
    public ResponseEntity<PlatformAuditPageResponse> searchEvents(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "actorId", required = false) String actorId,
            @RequestParam(value = "eventType", required = false) String eventType,
            @RequestParam(value = "correlationId", required = false) String correlationId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to
    ) {
        PlatformAuditPageResponse response = platformAuditService.query(
            tenantId,
            actorId,
            eventType,
            correlationId,
            from,
            to,
            page,
            size
        );
        return ResponseEntity.ok(response);
    }
}
