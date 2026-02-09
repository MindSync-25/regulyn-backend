package com.regulyn.vendor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.model.ActorType;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.events.util.EventHasher;
import com.regulyn.events.util.EventJson;
import com.regulyn.vendor.client.EvidenceClient;
import com.regulyn.vendor.model.VendorAccessEventEntity;
import com.regulyn.vendor.model.VendorAccessExportEntity;
import com.regulyn.vendor.repository.VendorAccessEventRepository;
import com.regulyn.vendor.repository.VendorAccessEventSpecifications;
import com.regulyn.vendor.repository.VendorAccessExportRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class VendorAccessExportProcessor {

    private static final String ENTITY_TYPE = "VENDOR_ACCESS_EXPORT";
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final VendorAccessExportRepository exportRepository;
    private final VendorAccessEventRepository eventRepository;
    private final EvidenceClient evidenceClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final String serviceName;
    private final int batchSize;

    public VendorAccessExportProcessor(
        VendorAccessExportRepository exportRepository,
        VendorAccessEventRepository eventRepository,
        EvidenceClient evidenceClient,
        AuditWriter auditWriter,
        OutboxWriter outboxWriter,
        ObjectMapper objectMapper,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager,
        @Value("${spring.application.name:vendor-sharing-service}") String serviceName,
        @Value("${vendor.access.export.batchSize:10}") int batchSize
    ) {
        this.exportRepository = exportRepository;
        this.eventRepository = eventRepository;
        this.evidenceClient = evidenceClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.serviceName = serviceName;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${vendor.access.export.cron:0 0 2 * * *}")
    public void processPendingExports() {
        List<UUID> pendingIds = claimPendingExports();
        for (UUID exportId : pendingIds) {
            processExport(exportId);
        }
    }

    public boolean processExport(UUID exportId, UUID tenantId) {
        Optional<VendorAccessExportEntity> maybe = exportRepository.findByTenantIdAndExportId(tenantId, exportId);
        if (maybe.isEmpty()) {
            return false;
        }
        processExport(exportId);
        return true;
    }

    public void processExport(UUID exportId) {
        Optional<VendorAccessExportEntity> maybe = exportRepository.findById(exportId);
        if (maybe.isEmpty()) {
            return;
        }
        VendorAccessExportEntity export = maybe.get();

        if ("CREATED".equals(export.getStatus()) && export.getArtifactRef() != null) {
            return;
        }

        if (!"PROCESSING".equals(export.getStatus())) {
            transactionTemplate.execute(status -> {
                VendorAccessExportEntity current = exportRepository.findById(exportId).orElse(null);
                if (current != null && "REQUESTED".equals(current.getStatus())) {
                    current.setStatus("PROCESSING");
                    exportRepository.save(current);
                }
                return null;
            });
        }

        try {
            ExportFilters filters = parseFilters(export.getNotes());
            List<VendorAccessEventEntity> events = loadEvents(export, filters);

            byte[] exportBytes = buildExportBytes(export.getFormat(), events);
            String sha256 = sha256(exportBytes);

            String filename = buildFilename(export.getVendorId(), export.getRangeStart(), export.getRangeEnd(), export.getFormat());
            String contentType = export.getFormat().equals("CSV") ? "text/csv" : "application/json";

            EvidenceClient.EvidenceArtifactResponse artifact = evidenceClient.createArtifact(
                export.getTenantId(),
                "VENDOR_ACCESS_LOG_EXPORT",
                filename,
                contentType,
                sha256,
                exportBytes
            );

            Counts counts = computeCounts(events);

            transactionTemplate.execute(status -> {
                VendorAccessExportEntity current = exportRepository.findById(exportId).orElse(null);
                if (current == null) {
                    return null;
                }
                current.setStatus("CREATED");
                current.setArtifactRef(artifact.artifactRef());
                current.setFileHash(artifact.sha256());
                current.setTotalEvents(counts.total);
                current.setAllowedEvents(counts.allowed);
                current.setDeniedEvents(counts.denied);
                current.setErrorEvents(counts.error);
                current.setNotes(mergeNotes(current.getNotes(), null));
                exportRepository.save(current);

                Map<String, Object> payload = buildPayload(current, filters, counts);
                writeAuditAndOutbox(
                    current.getTenantId(),
                    current.getRequestedByUserId(),
                    "VENDOR_ACCESS_EXPORT_CREATED",
                    "vendor_access.export_created",
                    current.getExportId(),
                    payload,
                    current.getIdempotencyKey()
                );
                writeAuditAndOutbox(
                    current.getTenantId(),
                    current.getRequestedByUserId(),
                    "VENDOR_ACCESS_EVIDENCE_ARTIFACT_STORED",
                    "vendor_access.evidence_artifact_stored",
                    current.getExportId(),
                    payload,
                    current.getIdempotencyKey()
                );
                return null;
            });
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "Export failed" : ex.getMessage();
            String trimmed = message.length() > 500 ? message.substring(0, 500) : message;

            transactionTemplate.execute(status -> {
                VendorAccessExportEntity current = exportRepository.findById(exportId).orElse(null);
                if (current == null) {
                    return null;
                }
                current.setStatus("FAILED");
                current.setNotes(mergeNotes(current.getNotes(), trimmed));
                exportRepository.save(current);

                Map<String, Object> payload = new HashMap<>();
                payload.put("tenantId", current.getTenantId().toString());
                payload.put("vendorId", current.getVendorId().toString());
                payload.put("exportId", current.getExportId().toString());
                payload.put("status", current.getStatus());
                payload.put("error", trimmed);
                writeAuditAndOutbox(
                    current.getTenantId(),
                    current.getRequestedByUserId(),
                    "VENDOR_ACCESS_EXPORT_FAILED",
                    "vendor_access.export_failed",
                    current.getExportId(),
                    payload,
                    current.getIdempotencyKey()
                );
                return null;
            });
        }
    }

    private List<UUID> claimPendingExports() {
        return transactionTemplate.execute(status -> {
            List<UUID> ids = jdbcTemplate.query(
                "SELECT export_id FROM vendor.vendor_access_exports WHERE status = 'REQUESTED' ORDER BY requested_at LIMIT ? FOR UPDATE SKIP LOCKED",
                new Object[] { batchSize },
                (rs, rowNum) -> UUID.fromString(rs.getString("export_id"))
            );
            for (UUID id : ids) {
                jdbcTemplate.update(
                    "UPDATE vendor.vendor_access_exports SET status = 'PROCESSING' WHERE export_id = ?",
                    id
                );
            }
            return ids;
        });
    }

    private List<VendorAccessEventEntity> loadEvents(VendorAccessExportEntity export, ExportFilters filters) {
        Specification<VendorAccessEventEntity> spec = VendorAccessEventSpecifications.tenantScoped(export.getTenantId())
            .and(VendorAccessEventSpecifications.accessedBetween(export.getRangeStart(), export.getRangeEnd()))
            .and(VendorAccessEventSpecifications.vendorIdEquals(export.getVendorId()));

        if (filters.systemName != null) {
            spec = spec.and(VendorAccessEventSpecifications.systemNameEquals(filters.systemName));
        }
        if (filters.accessType != null) {
            spec = spec.and(VendorAccessEventSpecifications.accessTypeEquals(filters.accessType));
        }
        if (filters.result != null) {
            spec = spec.and(VendorAccessEventSpecifications.resultEquals(filters.result));
        }

        return eventRepository.findAll(spec, Sort.by(Sort.Order.asc("accessedAt")));
    }

    private byte[] buildExportBytes(String format, List<VendorAccessEventEntity> events) throws Exception {
        if ("CSV".equalsIgnoreCase(format)) {
            return buildCsv(events).getBytes(StandardCharsets.UTF_8);
        }
        return objectMapper.writeValueAsBytes(buildJson(events));
    }

    private String buildCsv(List<VendorAccessEventEntity> events) {
        StringBuilder builder = new StringBuilder();
        builder.append("accessEventId,vendorId,systemName,accessType,subjectRef,dataCategories,purposeRef,purposeVersion,accessedAt,correlationId,actorType,actorId,ip,userAgent,result,rawPayloadHash,receivedAt");
        builder.append("\n");

        for (VendorAccessEventEntity event : events) {
            List<String> row = List.of(
                safe(event.getAccessEventId()),
                safe(event.getVendorId()),
                safe(event.getSystemName()),
                safe(event.getAccessType()),
                safe(event.getSubjectRef()),
                safe(joinList(event.getDataCategories())),
                safe(event.getPurposeRef()),
                safe(event.getPurposeVersion()),
                safe(event.getAccessedAt()),
                safe(event.getCorrelationId()),
                safe(event.getActorType()),
                safe(event.getActorId()),
                safe(event.getIp()),
                safe(event.getUserAgent()),
                safe(event.getResult()),
                safe(event.getRawPayloadHash()),
                safe(event.getReceivedAt())
            );
            builder.append(String.join(",", row)).append("\n");
        }
        return builder.toString();
    }

    private List<Map<String, Object>> buildJson(List<VendorAccessEventEntity> events) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (VendorAccessEventEntity event : events) {
            Map<String, Object> row = new HashMap<>();
            row.put("accessEventId", event.getAccessEventId());
            row.put("vendorId", event.getVendorId());
            row.put("systemName", event.getSystemName());
            row.put("accessType", event.getAccessType());
            row.put("subjectRef", event.getSubjectRef());
            row.put("dataCategories", event.getDataCategories());
            row.put("purposeRef", event.getPurposeRef());
            row.put("purposeVersion", event.getPurposeVersion());
            row.put("accessedAt", event.getAccessedAt());
            row.put("correlationId", event.getCorrelationId());
            row.put("actorType", event.getActorType());
            row.put("actorId", event.getActorId());
            row.put("ip", event.getIp());
            row.put("userAgent", event.getUserAgent());
            row.put("result", event.getResult());
            row.put("rawPayloadHash", event.getRawPayloadHash());
            row.put("receivedAt", event.getReceivedAt());
            records.add(row);
        }
        return records;
    }

    private String safe(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains("\"") || text.contains(",") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(";", values);
    }

    private String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        return bytesToHex(hash);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private String buildFilename(UUID vendorId, OffsetDateTime from, OffsetDateTime to, String format) {
        String ext = "CSV".equalsIgnoreCase(format) ? "csv" : "json";
        String ts = OffsetDateTime.now().format(FILE_TS);
        return String.format(Locale.ROOT, "vendor_access_%s_%s_%s_%s.%s", vendorId, ts, from.toEpochSecond(), to.toEpochSecond(), ext);
    }

    private Counts computeCounts(List<VendorAccessEventEntity> events) {
        long total = events.size();
        long allowed = events.stream().filter(e -> "ALLOWED".equalsIgnoreCase(e.getResult())).count();
        long denied = events.stream().filter(e -> "DENIED".equalsIgnoreCase(e.getResult())).count();
        long error = events.stream().filter(e -> "ERROR".equalsIgnoreCase(e.getResult())).count();
        return new Counts(total, allowed, denied, error);
    }

    private ExportFilters parseFilters(String notes) {
        if (notes == null || notes.isBlank()) {
            return new ExportFilters(null, null, null);
        }
        try {
            JsonNode node = objectMapper.readTree(notes);
            JsonNode filtersNode = node.get("filters");
            if (filtersNode == null || filtersNode.isNull()) {
                return new ExportFilters(null, null, null);
            }
            String systemName = text(filtersNode.get("systemName"));
            String accessType = text(filtersNode.get("accessType"));
            String result = text(filtersNode.get("result"));
            return new ExportFilters(systemName, accessType, result);
        } catch (Exception ex) {
            return new ExportFilters(null, null, null);
        }
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private String mergeNotes(String existingNotes, String error) {
        try {
            Map<String, Object> merged = new HashMap<>();
            if (existingNotes != null && !existingNotes.isBlank()) {
                Map<String, Object> existing = objectMapper.readValue(existingNotes, new TypeReference<Map<String, Object>>() {});
                merged.putAll(existing);
            }
            if (error != null) {
                merged.put("error", error);
            }
            return merged.isEmpty() ? null : objectMapper.writeValueAsString(merged);
        } catch (Exception ex) {
            return error != null ? error : existingNotes;
        }
    }

    private Map<String, Object> buildPayload(VendorAccessExportEntity export, ExportFilters filters, Counts counts) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", export.getTenantId().toString());
        payload.put("vendorId", export.getVendorId().toString());
        payload.put("exportId", export.getExportId().toString());
        payload.put("rangeStart", export.getRangeStart());
        payload.put("rangeEnd", export.getRangeEnd());
        payload.put("format", export.getFormat());
        payload.put("status", export.getStatus());
        payload.put("total", counts.total);
        payload.put("allowed", counts.allowed);
        payload.put("denied", counts.denied);
        payload.put("error", counts.error);
        if (filters.systemName != null) {
            payload.put("systemName", filters.systemName);
        }
        if (filters.accessType != null) {
            payload.put("accessType", filters.accessType);
        }
        if (filters.result != null) {
            payload.put("result", filters.result);
        }
        if (export.getArtifactRef() != null) {
            payload.put("artifactRef", export.getArtifactRef());
        }
        if (export.getFileHash() != null) {
            payload.put("fileHash", export.getFileHash());
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

    private record ExportFilters(String systemName, String accessType, String result) {
    }

    private record Counts(long total, long allowed, long denied, long error) {
    }
}
