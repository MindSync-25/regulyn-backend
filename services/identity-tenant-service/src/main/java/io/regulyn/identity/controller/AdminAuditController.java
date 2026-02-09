package io.regulyn.identity.controller;

import com.regulyn.auth.context.TenantContextHolder;
import io.regulyn.identity.dto.AuditEventPageResponse;
import io.regulyn.identity.service.AuditEventQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
public class AdminAuditController {

    private final AuditEventQueryService auditEventQueryService;

    public AdminAuditController(AuditEventQueryService auditEventQueryService) {
        this.auditEventQueryService = auditEventQueryService;
    }

    @GetMapping("/audit-events")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<AuditEventPageResponse> getAuditEvents(
            @RequestParam(value = "userId", required = false) UUID userId,
            @RequestParam(value = "eventType", required = false) String eventType,
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to", required = false) Instant to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        UUID tenantId = TenantContextHolder.getTenantId();
        AuditEventPageResponse response = auditEventQueryService.query(tenantId, userId, eventType, from, to, page, size);
        return ResponseEntity.ok(response);
    }
}