package io.regulyn.connector.controller;

import com.regulyn.auth.context.TenantContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/connector")
public class ConnectorController {

    @GetMapping("/ping")
    @PreAuthorize("hasRole('CONNECTOR_AGENT')")
    public ResponseEntity<Map<String, Object>> ping() {
        var context = TenantContextHolder.getContext();
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "timestamp", Instant.now(),
            "tenantId", context.getTenantId(),
            "message", "Connector service is running"
        ));
    }
}
