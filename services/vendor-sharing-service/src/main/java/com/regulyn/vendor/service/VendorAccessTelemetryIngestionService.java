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
import com.regulyn.vendor.dto.IngestVendorAccessEventsResponse;
import com.regulyn.vendor.dto.VendorAccessTelemetryEventIngestDto;
import com.regulyn.vendor.model.VendorAccessEventEntity;
import com.regulyn.vendor.repository.VendorAccessEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class VendorAccessTelemetryIngestionService {

    private static final Logger log = LoggerFactory.getLogger(VendorAccessTelemetryIngestionService.class);
    private static final int MAX_RAW_PAYLOAD_BYTES = 16 * 1024;
    private static final Pattern HASH_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");
    private static final String ENTITY_TYPE = "VENDOR_ACCESS_EVENT";
    private static final String ACTION_INGESTED = "VENDOR_ACCESS_EVENT_INGESTED";
    private static final String ACTION_DUPLICATE = "VENDOR_ACCESS_EVENT_DUPLICATE_IGNORED";
    private static final String OUTBOX_INGESTED = "vendor_access.event_ingested";
    private static final String OUTBOX_DUPLICATE = "vendor_access.event_duplicate_ignored";
    private static final String UNIQUE_CONSTRAINT = "uq_vendor_access_events_idempotency";

    private final VendorAccessEventRepository eventRepository;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final String serviceName;

    public VendorAccessTelemetryIngestionService(
        VendorAccessEventRepository eventRepository,
        AuditWriter auditWriter,
        OutboxWriter outboxWriter,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager,
        @Value("${spring.application.name:vendor-sharing-service}") String serviceName
    ) {
        this.eventRepository = eventRepository;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.serviceName = serviceName;
    }

    public IngestVendorAccessEventsResponse ingestBatch(
        UUID tenantId,
        UUID userId,
        List<VendorAccessTelemetryEventIngestDto> events
    ) {
        IngestVendorAccessEventsResponse response = new IngestVendorAccessEventsResponse();
        response.setReceived(events != null ? events.size() : 0);

        if (events == null || events.isEmpty()) {
            return response;
        }

        for (VendorAccessTelemetryEventIngestDto event : events) {
            IngestVendorAccessEventsResponse.IngestResult result = handleSingleEvent(tenantId, userId, event);
            response.addResult(result);

            switch (result.getStatus()) {
                case "INSERTED" -> response.setInserted(response.getInserted() + 1);
                case "DUPLICATE" -> response.setDuplicates(response.getDuplicates() + 1);
                case "FAILED" -> response.setFailed(response.getFailed() + 1);
                default -> {
                    // no-op
                }
            }
        }

        log.info("Vendor access ingestion summary: received={}, inserted={}, duplicates={}, failed={}",
            response.getReceived(), response.getInserted(), response.getDuplicates(), response.getFailed());

        return response;
    }

    private IngestVendorAccessEventsResponse.IngestResult handleSingleEvent(
        UUID tenantId,
        UUID userId,
        VendorAccessTelemetryEventIngestDto dto
    ) {
        IngestVendorAccessEventsResponse.IngestResult result = new IngestVendorAccessEventsResponse.IngestResult();
        result.setCorrelationId(dto != null ? dto.getCorrelationId() : null);
        result.setAccessType(dto != null ? dto.getAccessType() : null);
        result.setAccessedAt(dto != null ? dto.getAccessedAt() : null);

        String validationError = validate(dto);
        if (validationError != null) {
            result.setStatus("FAILED");
            result.setReason(validationError);
            return result;
        }

        VendorAccessEventEntity entity = mapEntity(tenantId, dto);
        String rawPayloadHash = entity.getRawPayloadHash();
        Map<String, Object> payload = buildPayload(tenantId, dto, rawPayloadHash);

        try {
            transactionTemplate.execute(status -> {
                eventRepository.save(entity);
                UUID eventId = entity.getAccessEventId();
                writeAuditAndOutbox(tenantId, userId, ACTION_INGESTED, OUTBOX_INGESTED, eventId, payload, rawPayloadHash, dto.getCorrelationId());
                return null;
            });

            result.setStatus("INSERTED");
            return result;
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateConstraint(ex)) {
                transactionTemplate.execute(status -> {
                    writeAuditAndOutbox(tenantId, userId, ACTION_DUPLICATE, OUTBOX_DUPLICATE, dto.getVendorId(), payload, rawPayloadHash, dto.getCorrelationId());
                    return null;
                });
                result.setStatus("DUPLICATE");
                return result;
            }

            log.debug("Failed to ingest vendor access event: correlationId={}, error={}", dto.getCorrelationId(), ex.getMessage());
            result.setStatus("FAILED");
            result.setReason("db_error");
            return result;
        } catch (Exception ex) {
            log.debug("Failed to ingest vendor access event: correlationId={}, error={}", dto.getCorrelationId(), ex.getMessage());
            result.setStatus("FAILED");
            result.setReason("unexpected_error");
            return result;
        }
    }

    private String validate(VendorAccessTelemetryEventIngestDto dto) {
        if (dto == null) {
            return "event_missing";
        }
        if (dto.getVendorId() == null) {
            return "vendor_id_required";
        }
        if (isBlank(dto.getSystemName())) {
            return "system_name_required";
        }
        if (isBlank(dto.getAccessType())) {
            return "access_type_required";
        }
        if (dto.getAccessedAt() == null) {
            return "accessed_at_required";
        }
        if (isBlank(dto.getCorrelationId())) {
            return "correlation_id_required";
        }
        if (isBlank(dto.getActorType())) {
            return "actor_type_required";
        }
        if (isBlank(dto.getResult())) {
            return "result_required";
        }
        return null;
    }

    private VendorAccessEventEntity mapEntity(UUID tenantId, VendorAccessTelemetryEventIngestDto dto) {
        VendorAccessEventEntity entity = new VendorAccessEventEntity();
        entity.setTenantId(tenantId);
        entity.setVendorId(dto.getVendorId());
        entity.setSystemName(dto.getSystemName());
        entity.setSource(dto.getSource());
        entity.setAccessType(dto.getAccessType());
        entity.setSubjectRef(dto.getSubjectRef());
        entity.setDataCategories(dto.getDataCategories());
        entity.setPurposeRef(dto.getPurposeRef());
        entity.setPurposeVersion(dto.getPurposeVersion());
        entity.setAccessedAt(dto.getAccessedAt());
        entity.setCorrelationId(dto.getCorrelationId());
        entity.setActorType(dto.getActorType());
        entity.setActorId(dto.getActorId());
        entity.setIp(dto.getIp());
        entity.setUserAgent(dto.getUserAgent());
        entity.setResult(dto.getResult());

        String rawPayloadHash = resolveRawPayloadHash(dto);
        entity.setRawPayloadHash(rawPayloadHash);
        entity.setRawPayloadRef(trimRawPayloadRef(dto.getRawPayloadRef()));

        return entity;
    }

    private String resolveRawPayloadHash(VendorAccessTelemetryEventIngestDto dto) {
        if (dto.getRawPayloadHash() != null && HASH_PATTERN.matcher(dto.getRawPayloadHash()).matches()) {
            return dto.getRawPayloadHash().toLowerCase();
        }

        if (dto.getRawPayloadRef() != null) {
            String canonicalRaw = EventJson.toCanonicalJson(dto.getRawPayloadRef());
            return EventHasher.sha256(canonicalRaw);
        }

        Map<String, Object> sanitized = new HashMap<>(objectMapper.convertValue(dto, Map.class));
        sanitized.remove("rawPayloadHash");
        String canonical = EventJson.toCanonicalJson(sanitized);
        return EventHasher.sha256(canonical);
    }

    private Map<String, Object> trimRawPayloadRef(Map<String, Object> rawPayloadRef) {
        if (rawPayloadRef == null) {
            return null;
        }
        try {
            byte[] payloadBytes = objectMapper.writeValueAsBytes(rawPayloadRef);
            if (payloadBytes.length <= MAX_RAW_PAYLOAD_BYTES) {
                return rawPayloadRef;
            }
            Map<String, Object> trimmed = new HashMap<>();
            trimmed.put("truncated", true);
            trimmed.put("originalSize", payloadBytes.length);
            trimmed.put("note", "raw payload truncated by vendor-sharing-service");
            return trimmed;
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("truncated", true);
            fallback.put("note", "raw payload truncation failed");
            return fallback;
        }
    }

    private Map<String, Object> buildPayload(UUID tenantId, VendorAccessTelemetryEventIngestDto dto, String rawPayloadHash) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("vendorId", dto.getVendorId().toString());
        payload.put("systemName", dto.getSystemName());
        payload.put("accessType", dto.getAccessType());
        payload.put("accessedAt", dto.getAccessedAt());
        payload.put("correlationId", dto.getCorrelationId());
        payload.put("result", dto.getResult());
        payload.put("rawPayloadHash", rawPayloadHash);
        payload.put("actorType", dto.getActorType());
        if (!isBlank(dto.getActorId())) {
            payload.put("actorId", dto.getActorId());
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
        String payloadHash,
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
            .payloadHash(payloadHash)
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

    private boolean isDuplicateConstraint(DataIntegrityViolationException ex) {
        Throwable mostSpecific = ex.getMostSpecificCause();
        if (mostSpecific != null) {
            String message = mostSpecific.getMessage();
            if (message != null && message.contains(UNIQUE_CONSTRAINT)) {
                return true;
            }
            if (mostSpecific instanceof org.postgresql.util.PSQLException psql) {
                return "23505".equals(psql.getSQLState());
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
