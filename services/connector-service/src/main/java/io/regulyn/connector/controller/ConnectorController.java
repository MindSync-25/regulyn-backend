package io.regulyn.connector.controller;

import io.regulyn.connector.dto.ConnectorResponse;
import io.regulyn.connector.dto.CreateConnectorRequest;
import io.regulyn.connector.dto.CreateTargetRequest;
import io.regulyn.connector.dto.TargetResponse;
import io.regulyn.connector.service.ConnectorService;
import io.regulyn.connector.service.TargetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/connectors")
public class ConnectorController {

    private final ConnectorService connectorService;
    private final TargetService targetService;

    public ConnectorController(ConnectorService connectorService, TargetService targetService) {
        this.connectorService = connectorService;
        this.targetService = targetService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CONNECTOR_AGENT') or hasRole('ADMIN')")
    public ResponseEntity<ConnectorResponse> createConnector(@Valid @RequestBody CreateConnectorRequest request) {
        ConnectorResponse response = connectorService.createConnector(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('CONNECTOR_AGENT') or hasRole('ADMIN')")
    public ResponseEntity<List<ConnectorResponse>> listConnectors(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String q) {
        List<ConnectorResponse> connectors = connectorService.listConnectors(status, type);
        return ResponseEntity.ok(connectors);
    }

    @GetMapping("/{connectorId}")
    @PreAuthorize("hasRole('CONNECTOR_AGENT') or hasRole('ADMIN')")
    public ResponseEntity<ConnectorResponse> getConnector(@PathVariable UUID connectorId) {
        ConnectorResponse connector = connectorService.getConnector(connectorId);
        return ResponseEntity.ok(connector);
    }

    @PostMapping("/{connectorId}/disable")
    @PreAuthorize("hasRole('CONNECTOR_AGENT') or hasRole('ADMIN')")
    public ResponseEntity<ConnectorResponse> disableConnector(@PathVariable UUID connectorId) {
        ConnectorResponse response = connectorService.disableConnector(connectorId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{connectorId}/targets")
    @PreAuthorize("hasRole('CONNECTOR_AGENT') or hasRole('ADMIN')")
    public ResponseEntity<TargetResponse> createTarget(
            @PathVariable UUID connectorId,
            @Valid @RequestBody CreateTargetRequest request) {
        TargetResponse response = targetService.createTarget(connectorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{connectorId}/targets")
    @PreAuthorize("hasRole('CONNECTOR_AGENT') or hasRole('ADMIN')")
    public ResponseEntity<List<TargetResponse>> listTargets(@PathVariable UUID connectorId) {
        List<TargetResponse> targets = targetService.listTargets(connectorId);
        return ResponseEntity.ok(targets);
    }
}
