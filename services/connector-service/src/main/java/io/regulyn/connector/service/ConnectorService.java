package io.regulyn.connector.service;

import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import io.regulyn.connector.dto.ConnectorResponse;
import io.regulyn.connector.dto.CreateConnectorRequest;
import io.regulyn.connector.model.Connector;
import io.regulyn.connector.repository.ConnectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ConnectorService {

    private static final Logger log = LoggerFactory.getLogger(ConnectorService.class);

    private final ConnectorRepository connectorRepository;
    private final OutboxWriter outboxWriter;

    public ConnectorService(ConnectorRepository connectorRepository, OutboxWriter outboxWriter) {
        this.connectorRepository = connectorRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public ConnectorResponse createConnector(CreateConnectorRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        try {
            // Check for duplicate name
            if (connectorRepository.existsByTenantIdAndConnectorName(tenantId, request.getConnectorName())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Connector with name '" + request.getConnectorName() + "' already exists");
            }

            Connector connector = new Connector();
            connector.setTenantId(tenantId);
            connector.setConnectorName(request.getConnectorName());
            connector.setConnectorType(request.getConnectorType().name());
            connector.setStatus(request.getStatus().name());
            connector.setBaseUrl(request.getBaseUrl());
            connector.setAuthType(request.getAuthType().name());
            connector.setAuthRef(request.getAuthRef());
            connector.setMetadata(request.getMetadata());

            connector = connectorRepository.save(connector);

            log.info("Created connector {} for tenant {}", connector.getConnectorId(), tenantId);

            // Emit event
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("connectorId", connector.getConnectorId().toString());
            eventPayload.put("connectorName", connector.getConnectorName());
            eventPayload.put("connectorType", connector.getConnectorType());

            EventEnvelopeV1 event = EventFactory.create(
                    "connector.created",
                    "connector-service",
                    "connector",
                    connector.getConnectorId().toString(),
                    eventPayload
            );
            outboxWriter.write(event);

            return toConnectorResponse(connector);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating connector for tenant {}", tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create connector", e);
        }
    }

    public List<ConnectorResponse> listConnectors(String status, String type) {
        UUID tenantId = TenantContextHolder.getTenantId();

        try {
            List<Connector> connectors;

            if (status != null && !status.isBlank()) {
                connectors = connectorRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
            } else if (type != null && !type.isBlank()) {
                connectors = connectorRepository.findByTenantIdAndConnectorTypeOrderByCreatedAtDesc(tenantId, type);
            } else {
                connectors = connectorRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
            }

            return connectors.stream()
                    .map(this::toConnectorResponse)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error listing connectors for tenant {}", tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to list connectors", e);
        }
    }

    public ConnectorResponse getConnector(UUID connectorId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        Connector connector = connectorRepository.findByTenantIdAndConnectorId(tenantId, connectorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector not found"));

        return toConnectorResponse(connector);
    }

    @Transactional
    public ConnectorResponse disableConnector(UUID connectorId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        try {
            Connector connector = connectorRepository.findByTenantIdAndConnectorId(tenantId, connectorId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector not found"));

            connector.setStatus("DISABLED");
            connector.setUpdatedAt(Instant.now());
            connector = connectorRepository.save(connector);

            log.info("Disabled connector {} for tenant {}", connectorId, tenantId);

            // Emit event
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("connectorId", connector.getConnectorId().toString());
            eventPayload.put("status", "DISABLED");

            EventEnvelopeV1 event = EventFactory.create(
                    "connector.disabled",
                    "connector-service",
                    "connector",
                    connector.getConnectorId().toString(),
                    eventPayload
            );
            outboxWriter.write(event);

            return toConnectorResponse(connector);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error disabling connector {} for tenant {}", connectorId, tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to disable connector", e);
        }
    }

    private ConnectorResponse toConnectorResponse(Connector connector) {
        ConnectorResponse response = new ConnectorResponse();
        response.setConnectorId(connector.getConnectorId());
        response.setTenantId(connector.getTenantId());
        response.setConnectorName(connector.getConnectorName());
        response.setConnectorType(connector.getConnectorType());
        response.setStatus(connector.getStatus());
        response.setBaseUrl(connector.getBaseUrl());
        response.setAuthType(connector.getAuthType());
        response.setAuthRef(connector.getAuthRef());
        response.setMetadata(connector.getMetadata());
        response.setCreatedAt(connector.getCreatedAt());
        response.setUpdatedAt(connector.getUpdatedAt());
        return response;
    }
}
