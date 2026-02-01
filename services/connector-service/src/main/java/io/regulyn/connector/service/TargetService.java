package io.regulyn.connector.service;

import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import io.regulyn.connector.dto.CreateTargetRequest;
import io.regulyn.connector.dto.TargetResponse;
import io.regulyn.connector.model.ConnectorTarget;
import io.regulyn.connector.repository.ConnectorRepository;
import io.regulyn.connector.repository.ConnectorTargetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TargetService {

    private static final Logger log = LoggerFactory.getLogger(TargetService.class);

    private final ConnectorTargetRepository targetRepository;
    private final ConnectorRepository connectorRepository;
    private final OutboxWriter outboxWriter;

    public TargetService(ConnectorTargetRepository targetRepository,
                        ConnectorRepository connectorRepository,
                        OutboxWriter outboxWriter) {
        this.targetRepository = targetRepository;
        this.connectorRepository = connectorRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public TargetResponse createTarget(UUID connectorId, CreateTargetRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        try {
            // Verify connector exists and belongs to tenant
            connectorRepository.findByTenantIdAndConnectorId(tenantId, connectorId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector not found"));

            // Check for duplicate target key
            if (targetRepository.existsByTenantIdAndConnectorIdAndTargetKey(tenantId, connectorId, request.getTargetKey())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Target with key '" + request.getTargetKey() + "' already exists for this connector");
            }

            ConnectorTarget target = new ConnectorTarget();
            target.setTenantId(tenantId);
            target.setConnectorId(connectorId);
            target.setTargetKey(request.getTargetKey());
            target.setTargetType(request.getTargetType().name());
            target.setSubjectType(request.getSubjectType().name());
            target.setSupportedActions(request.getSupportedActions().stream()
                    .map(Enum::name)
                    .collect(Collectors.toList()));
            target.setRequiresApproval(request.getRequiresApproval());
            target.setMetadata(request.getMetadata());

            target = targetRepository.save(target);

            log.info("Created target {} for connector {} tenant {}", target.getTargetId(), connectorId, tenantId);

            // Emit event
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("targetId", target.getTargetId().toString());
            eventPayload.put("connectorId", connectorId.toString());
            eventPayload.put("targetKey", target.getTargetKey());

            EventEnvelopeV1 event = EventFactory.create(
                    "connector.target_created",
                    "connector-service",
                    "connector_target",
                    target.getTargetId().toString(),
                    eventPayload
            );
            outboxWriter.write(event);

            return toTargetResponse(target);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating target for connector {} tenant {}", connectorId, tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create target", e);
        }
    }

    public List<TargetResponse> listTargets(UUID connectorId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        try {
            // Verify connector exists
            connectorRepository.findByTenantIdAndConnectorId(tenantId, connectorId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector not found"));

            List<ConnectorTarget> targets = targetRepository.findByTenantIdAndConnectorIdOrderByCreatedAtDesc(tenantId, connectorId);

            return targets.stream()
                    .map(this::toTargetResponse)
                    .collect(Collectors.toList());

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error listing targets for connector {} tenant {}", connectorId, tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to list targets", e);
        }
    }

    public TargetResponse getTarget(UUID targetId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        ConnectorTarget target = targetRepository.findByTenantIdAndTargetId(tenantId, targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target not found"));

        return toTargetResponse(target);
    }

    private TargetResponse toTargetResponse(ConnectorTarget target) {
        TargetResponse response = new TargetResponse();
        response.setTargetId(target.getTargetId());
        response.setTenantId(target.getTenantId());
        response.setConnectorId(target.getConnectorId());
        response.setTargetKey(target.getTargetKey());
        response.setTargetType(target.getTargetType());
        response.setSubjectType(target.getSubjectType());
        response.setSupportedActions(target.getSupportedActions());
        response.setRequiresApproval(target.getRequiresApproval());
        response.setMetadata(target.getMetadata());
        response.setCreatedAt(target.getCreatedAt());
        return response;
    }
}
