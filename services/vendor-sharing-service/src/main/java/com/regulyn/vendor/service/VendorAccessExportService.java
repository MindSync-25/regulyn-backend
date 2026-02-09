package com.regulyn.vendor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.model.ActorType;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.events.util.EventHasher;
import com.regulyn.events.util.EventJson;
import com.regulyn.vendor.dto.CreateVendorAccessExportRequest;
import com.regulyn.vendor.dto.CreateVendorAccessExportResponse;
import com.regulyn.vendor.dto.VendorAccessExportCountsDto;
import com.regulyn.vendor.model.VendorAccessExportEntity;
import com.regulyn.vendor.repository.VendorAccessExportRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class VendorAccessExportService {

    private static final String ENTITY_TYPE = "VENDOR_ACCESS_EXPORT";

    private final VendorAccessExportRepository repository;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final String serviceName;

    public VendorAccessExportService(
        VendorAccessExportRepository repository,
        AuditWriter auditWriter,
        OutboxWriter outboxWriter,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager,
        @Value("${spring.application.name:vendor-sharing-service}") String serviceName
    ) {
        this.repository = repository;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.serviceName = serviceName;
    }

    public CreateVendorAccessExportResponse requestExport(
        UUID tenantId,
        UUID userId,
        String idempotencyKey,
        CreateVendorAccessExportRequest request
    ) {
        Optional<VendorAccessExportEntity> existing = repository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        VendorAccessExportEntity export = new VendorAccessExportEntity();
        export.setTenantId(tenantId);
        export.setVendorId(request.getVendorId());
        export.setRequestedByUserId(userId);
        export.setRangeStart(request.getFrom());
        export.setRangeEnd(request.getTo());
        export.setFormat(request.getFormat());
        export.setStatus("REQUESTED");
        export.setIdempotencyKey(idempotencyKey);
        export.setTotalEvents(0);
        export.setAllowedEvents(0);
        export.setDeniedEvents(0);
        export.setErrorEvents(0);
        export.setNotes(buildFiltersNotes(request));

        transactionTemplate.execute(status -> {
            repository.save(export);
            writeAuditAndOutbox(
                tenantId,
                userId,
                "VENDOR_ACCESS_EXPORT_REQUESTED",
                "vendor_access.export_requested",
                export.getExportId(),
                buildPayload(export, request, idempotencyKey),
                idempotencyKey
            );
            return null;
        });

        return toResponse(export);
    }

    private String buildFiltersNotes(CreateVendorAccessExportRequest request) {
        try {
            Map<String, Object> filters = new HashMap<>();
            if (request.getSystemName() != null) {
                filters.put("systemName", request.getSystemName());
            }
            if (request.getAccessType() != null) {
                filters.put("accessType", request.getAccessType());
            }
            if (request.getResult() != null) {
                filters.put("result", request.getResult());
            }
            if (filters.isEmpty()) {
                return null;
            }
            Map<String, Object> wrapper = new HashMap<>();
            wrapper.put("filters", filters);
            return objectMapper.writeValueAsString(wrapper);
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> buildPayload(
        VendorAccessExportEntity export,
        CreateVendorAccessExportRequest request,
        String idempotencyKey
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", export.getTenantId().toString());
        payload.put("vendorId", export.getVendorId().toString());
        payload.put("exportId", export.getExportId().toString());
        payload.put("rangeStart", export.getRangeStart());
        payload.put("rangeEnd", export.getRangeEnd());
        payload.put("format", export.getFormat());
        payload.put("idempotencyKey", idempotencyKey);
        if (export.getRequestedByUserId() != null) {
            payload.put("requestedByUserId", export.getRequestedByUserId().toString());
        }
        if (request.getSystemName() != null) {
            payload.put("systemName", request.getSystemName());
        }
        if (request.getAccessType() != null) {
            payload.put("accessType", request.getAccessType());
        }
        if (request.getResult() != null) {
            payload.put("result", request.getResult());
        }
        return payload;
    }

    private void writeAuditAndOutbox(
        UUID tenantId,
        UUID userId,
        String action,
        String eventType,
        UUID entityId,
        Map<String, Object> payload,
        String correlationId
    ) {
        JsonNode metadata = objectMapper.valueToTree(payload);

        AuditEvent auditEvent = AuditEvent.builder()
            .tenantId(tenantId)
            .actorId(userId)
            .actorType(userId != null ? AuditEvent.ActorType.USER : AuditEvent.ActorType.SYSTEM)
            .service(serviceName)
            .action(action)
            .entityType(ENTITY_TYPE)
            .entityId(entityId.toString())
            .payloadHash(EventHasher.sha256(EventJson.toCanonicalJson(payload)))
            .metadata(metadata)
            .build();

        auditWriter.write(auditEvent);

        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setEventType(eventType);
        envelope.setTenantId(tenantId);
        envelope.setActorId(userId);
        envelope.setActorType(userId != null ? ActorType.USER : ActorType.SYSTEM);
        envelope.setSourceService(serviceName);
        envelope.setEntityType(ENTITY_TYPE);
        envelope.setEntityId(entityId.toString());
        envelope.setOccurredAt(Instant.now());
        envelope.setCorrelationId(correlationId);
        envelope.setPayload(EventJson.toJsonNode(payload));
        envelope.setPayloadHash(EventHasher.sha256(EventJson.toCanonicalJson(payload)));
        envelope.setSchemaVersion(1);

        outboxWriter.write(envelope);
    }

    private CreateVendorAccessExportResponse toResponse(VendorAccessExportEntity export) {
        CreateVendorAccessExportResponse response = new CreateVendorAccessExportResponse();
        response.setExportId(export.getExportId());
        response.setStatus(export.getStatus());
        response.setArtifactRef(export.getArtifactRef());
        response.setFileHash(export.getFileHash());
        response.setCounts(new VendorAccessExportCountsDto(
            export.getTotalEvents(),
            export.getAllowedEvents(),
            export.getDeniedEvents(),
            export.getErrorEvents()
        ));
        response.setRequestedAt(export.getRequestedAt());
        response.setRangeStart(export.getRangeStart());
        response.setRangeEnd(export.getRangeEnd());
        return response;
    }
}
