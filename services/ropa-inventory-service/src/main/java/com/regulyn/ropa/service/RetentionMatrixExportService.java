package com.regulyn.ropa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.ropa.api.dto.*;
import com.regulyn.ropa.client.EvidenceReportingClient;
import com.regulyn.ropa.model.*;
import com.regulyn.ropa.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RetentionMatrixExportService {

    private static final Logger log = LoggerFactory.getLogger(RetentionMatrixExportService.class);

    private final RopaReportExportRepository reportExportRepository;
    private final RopaActivityVersionRepository activityVersionRepository;
    private final RopaActivitySystemRepository activitySystemRepository;
    private final RopaActivityDataCategoryRepository activityDataCategoryRepository;
    private final RopaSystemRepository systemRepository;
    private final RetentionResolutionService resolutionService;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final EvidenceReportingClient evidenceReportingClient;
    private final ObjectMapper objectMapper;

    public RetentionMatrixExportService(
            RopaReportExportRepository reportExportRepository,
            RopaActivityVersionRepository activityVersionRepository,
            RopaActivitySystemRepository activitySystemRepository,
            RopaActivityDataCategoryRepository activityDataCategoryRepository,
            RopaSystemRepository systemRepository,
            RetentionResolutionService resolutionService,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter,
            EvidenceReportingClient evidenceReportingClient,
            ObjectMapper objectMapper) {
        this.reportExportRepository = reportExportRepository;
        this.activityVersionRepository = activityVersionRepository;
        this.activitySystemRepository = activitySystemRepository;
        this.activityDataCategoryRepository = activityDataCategoryRepository;
        this.systemRepository = systemRepository;
        this.resolutionService = resolutionService;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.evidenceReportingClient = evidenceReportingClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public RetentionMatrixExportResponse exportRetentionMatrix(UUID tenantId, UUID userId, String idempotencyKey,
                                                               RetentionMatrixExportRequest request) {
        String payloadHash = computeRequestHash(request);

        Optional<RopaReportExportEntity> existing = reportExportRepository
            .findByTenantIdAndReportTypeAndIdempotencyKey(tenantId, ReportType.RETENTION_MATRIX, idempotencyKey);
        if (existing.isPresent()) {
            if (existing.get().getPayloadHash() == null) {
                existing.get().setPayloadHash(payloadHash);
                reportExportRepository.save(existing.get());
            } else if (!Objects.equals(existing.get().getPayloadHash(), payloadHash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_PAYLOAD_MISMATCH");
            }
            if (existing.get().getStatus() != ReportStatus.CREATED || existing.get().getArtifactRef() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "EXPORT_IN_PROGRESS");
            }
            List<RetentionMatrixRow> rows = buildMatrixRows(tenantId, request);
            return buildResponse(existing.get(), rows);
        }

        RopaReportExportEntity report = new RopaReportExportEntity();
        report.setTenantId(tenantId);
        report.setReportType(ReportType.RETENTION_MATRIX);
        report.setStatus(ReportStatus.REQUESTED);
        report.setRequestedBy(userId);
        report.setIdempotencyKey(idempotencyKey);
        report.setRequestedAt(Instant.now());
        report = reportExportRepository.save(report);

        try {
            List<RetentionMatrixRow> rows = buildMatrixRows(tenantId, request);
            Map<String, Object> evidencePayload = buildEvidencePayload(report, request, rows);
            report.setPayloadHash(payloadHash);
            report = reportExportRepository.save(report);

            writeAudit(tenantId, userId, "RETENTION_MATRIX_EXPORT_REQUESTED", report.getId(), payloadHash,
                buildExportMetadata(report, request, 0, payloadHash));
            writeOutbox("ropa.retention_matrix_export_requested", report.getId().toString(), idempotencyKey,
                buildExportPayload(tenantId, userId, report, request, 0, payloadHash));

            EvidenceReportingClient.EvidenceArtifactResponse artifact = evidenceReportingClient.createArtifact(
                tenantId,
                userId,
                idempotencyKey,
                "ROPA_RETENTION_MATRIX",
                "ropa-retention-matrix-" + tenantId + "-" + report.getId() + ".json",
                "application/json",
                evidencePayload
            );

            report.setStatus(ReportStatus.CREATED);
            report.setCreatedAt(Instant.now());
            report.setArtifactRef(artifact.artifactRef());
            report.setArtifactHash(artifact.artifactHash());
            report = reportExportRepository.save(report);

            writeAudit(tenantId, userId, "RETENTION_MATRIX_EXPORT_CREATED", report.getId(), payloadHash,
                buildExportMetadata(report, request, rows.size(), payloadHash));
            writeOutbox("ropa.retention_matrix_export_created", report.getId().toString(), idempotencyKey,
                buildExportPayload(tenantId, userId, report, request, rows.size(), payloadHash));

            writeEvidenceStoredEvents(tenantId, userId, idempotencyKey, report, rows.size(), payloadHash);

            return buildResponse(report, rows);
        } catch (Exception e) {
            report.setStatus(ReportStatus.FAILED);
            report.setErrorCode("EXPORT_FAILED");
            report.setErrorMessage(e.getMessage());
            reportExportRepository.save(report);
            writeAudit(tenantId, userId, "RETENTION_MATRIX_EXPORT_FAILED", report.getId(),
                report.getPayloadHash() != null ? report.getPayloadHash() : "", buildFailureMetadata(report));
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "EVIDENCE_SERVICE_UNAVAILABLE");
        }
    }

    private RetentionMatrixExportResponse buildResponse(RopaReportExportEntity report, List<RetentionMatrixRow> rows) {
        RetentionMatrixExportResponse response = new RetentionMatrixExportResponse();
        response.setReportExportId(report.getId());
        response.setStatus(report.getStatus());
        response.setRows(rows);
        Instant generatedAt = report.getCreatedAt() != null ? report.getCreatedAt() : Instant.now();
        response.setGeneratedAt(generatedAt.truncatedTo(ChronoUnit.MICROS));
        response.setArtifactRef(report.getArtifactRef());
        response.setArtifactHash(report.getArtifactHash());
        return response;
    }

    private List<RetentionMatrixRow> buildMatrixRows(UUID tenantId, RetentionMatrixExportRequest request) {
        RopaActivityVersion.Status status = request.getActivityStatus() != null
            ? request.getActivityStatus()
            : RopaActivityVersion.Status.PUBLISHED;

        List<RopaSystem> systems = Boolean.TRUE.equals(request.getIncludeDisabled())
            ? systemRepository.findByTenantId(tenantId)
            : systemRepository.findByTenantIdAndEnabled(tenantId, true);

        Set<UUID> allowedSystemIds = request.getSystemIds() != null && !request.getSystemIds().isEmpty()
            ? new HashSet<>(request.getSystemIds())
            : systems.stream().map(RopaSystem::getSystemId).collect(Collectors.toSet());

        List<RopaActivityVersion> activities = activityVersionRepository.findByTenantIdAndStatus(tenantId, status);

        List<RetentionMatrixRow> rows = new ArrayList<>();
        for (RopaActivityVersion version : activities) {
            List<RopaActivitySystem> linkedSystems = activitySystemRepository.findByVersionId(version.getVersionId());
            List<RopaActivityDataCategory> linkedCategories = activityDataCategoryRepository.findByVersionId(version.getVersionId());

            for (RopaActivitySystem link : linkedSystems) {
                if (!allowedSystemIds.contains(link.getSystemId())) {
                    continue;
                }
                for (RopaActivityDataCategory category : linkedCategories) {
                    RetentionResolveResponse resolved = resolutionService.resolveEffectiveRetention(
                        tenantId,
                        link.getSystemId(),
                        version.getActivityId(),
                        null,
                        null,
                        false
                    );
                    RetentionEffectiveRetention effective = resolved.getEffective();

                    RetentionMatrixRow row = new RetentionMatrixRow();
                    row.setSystemId(link.getSystemId());
                    row.setActivityId(version.getActivityId());
                    row.setDataCategoryId(category.getDataCategoryId());
                    row.setPurposeVersionId(null);
                    row.setEffectiveLevel(effective.getLevel());
                    row.setRetentionDays(effective.getRetentionDays());
                    row.setRetentionBasis(effective.getRetentionBasis());
                    row.setRetentionNote(effective.getRetentionNote());
                    row.setReviewRequired(effective.getReviewRequired());
                    rows.add(row);
                }
            }
        }

        rows.sort(Comparator
            .comparing(RetentionMatrixRow::getSystemId, Comparator.nullsLast(UUID::compareTo))
            .thenComparing(RetentionMatrixRow::getActivityId, Comparator.nullsLast(UUID::compareTo))
            .thenComparing(RetentionMatrixRow::getDataCategoryId, Comparator.nullsLast(UUID::compareTo))
            .thenComparing(RetentionMatrixRow::getPurposeVersionId, Comparator.nullsLast(UUID::compareTo)));

        return rows;
    }

    private void writeAudit(UUID tenantId, UUID userId, String action, UUID entityId, String payloadHash, ObjectNode metadata) {
        try {
            AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(userId)
                .actorType(AuditEvent.ActorType.USER)
                .action(action)
                .entityType("ROPA_RETENTION_MATRIX_EXPORT")
                .entityId(entityId.toString())
                .payloadHash(payloadHash)
                .metadata(metadata)
                .build();
            auditWriter.write(auditEvent);
        } catch (Exception e) {
            log.error("Failed to write audit event: {}", e.getMessage());
        }
    }

    private void writeOutbox(String eventType, String entityId, String idempotencyKey, Map<String, Object> payload) {
        try {
            EventEnvelopeV1 event = EventFactory.create(eventType, "ropa-inventory-service", "ropa_retention_matrix_export", entityId, payload, idempotencyKey);
            outboxWriter.write(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", e.getMessage());
        }
    }

    private ObjectNode buildExportMetadata(RopaReportExportEntity report, RetentionMatrixExportRequest request, int rowCount, String payloadHash) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("reportExportId", report.getId().toString());
        node.put("reportType", report.getReportType().name());
        node.put("status", report.getStatus().name());
        node.put("rowCount", rowCount);
        if (payloadHash != null) {
            node.put("payloadHash", payloadHash);
        }
        node.put("activityStatus", request.getActivityStatus() != null ? request.getActivityStatus().name() : RopaActivityVersion.Status.PUBLISHED.name());
        node.put("includeDisabled", Boolean.TRUE.equals(request.getIncludeDisabled()));
        if (request.getSystemIds() != null && !request.getSystemIds().isEmpty()) {
            node.put("systemIds", String.join(",", request.getSystemIds().stream().map(UUID::toString).toList()));
        }
        if (report.getArtifactRef() != null) {
            node.put("artifactRef", report.getArtifactRef());
        }
        if (report.getArtifactHash() != null) {
            node.put("artifactHash", report.getArtifactHash());
        }
        return node;
    }

    private Map<String, Object> buildExportPayload(UUID tenantId, UUID userId, RopaReportExportEntity report,
                                                   RetentionMatrixExportRequest request, int rowCount, String payloadHash) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId.toString());
        if (userId != null) {
            payload.put("actorUserId", userId.toString());
        }
        payload.put("reportExportId", report.getId().toString());
        payload.put("reportType", report.getReportType().name());
        payload.put("status", report.getStatus().name());
        payload.put("rowCount", rowCount);
        payload.put("activityStatus", request.getActivityStatus() != null ? request.getActivityStatus().name() : RopaActivityVersion.Status.PUBLISHED.name());
        payload.put("includeDisabled", Boolean.TRUE.equals(request.getIncludeDisabled()));
        if (request.getSystemIds() != null && !request.getSystemIds().isEmpty()) {
            payload.put("systemIds", request.getSystemIds().stream().map(UUID::toString).toList());
        }
        payload.put("payloadHash", payloadHash);
        if (report.getArtifactRef() != null) {
            payload.put("artifactRef", report.getArtifactRef());
        }
        if (report.getArtifactHash() != null) {
            payload.put("artifactHash", report.getArtifactHash());
        }
        payload.put("idempotencyKey", report.getIdempotencyKey());
        return payload;
    }

    private Map<String, Object> buildEvidencePayload(RopaReportExportEntity report,
                                                     RetentionMatrixExportRequest request,
                                                     List<RetentionMatrixRow> rows) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("activityStatus", request.getActivityStatus() != null
            ? request.getActivityStatus().name()
            : RopaActivityVersion.Status.PUBLISHED.name());
        filters.put("includeDisabled", Boolean.TRUE.equals(request.getIncludeDisabled()));
        List<String> systemIds = request.getSystemIds() == null
            ? List.of()
            : request.getSystemIds().stream().map(UUID::toString).sorted().toList();
        filters.put("systemIds", systemIds);

        Map<String, Object> payload = new LinkedHashMap<>();
        Instant generatedAt = report.getRequestedAt() != null
            ? report.getRequestedAt()
            : report.getCreatedAt() != null ? report.getCreatedAt() : Instant.now();
        payload.put("generatedAt", generatedAt.truncatedTo(ChronoUnit.MICROS).toString());
        payload.put("reportExportId", report.getId().toString());
        payload.put("reportType", report.getReportType().name());
        payload.put("filters", filters);
        payload.put("rows", rows);
        payload.put("rowCount", rows.size());
        payload.put("schemaVersion", "1.0");
        return payload;
    }

    private String computePayloadHash(Map<String, Object> payload) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute hash", e);
        }
    }

    private String computeRequestHash(RetentionMatrixExportRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activityStatus", request.getActivityStatus() != null
            ? request.getActivityStatus().name()
            : RopaActivityVersion.Status.PUBLISHED.name());
        payload.put("includeDisabled", Boolean.TRUE.equals(request.getIncludeDisabled()));
        List<String> systemIds = request.getSystemIds() == null
            ? List.of()
            : request.getSystemIds().stream().map(UUID::toString).sorted().toList();
        payload.put("systemIds", systemIds);
        return computePayloadHash(payload);
    }

    private ObjectNode buildFailureMetadata(RopaReportExportEntity report) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("reportExportId", report.getId().toString());
        node.put("reportType", report.getReportType().name());
        node.put("status", report.getStatus().name());
        if (report.getErrorCode() != null) {
            node.put("errorCode", report.getErrorCode());
        }
        if (report.getErrorMessage() != null) {
            node.put("errorMessage", report.getErrorMessage());
        }
        return node;
    }

    private void writeEvidenceStoredEvents(UUID tenantId, UUID userId, String idempotencyKey,
                                           RopaReportExportEntity report, int rowCount, String payloadHash) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("reportExportId", report.getId().toString());
        metadata.put("reportType", report.getReportType().name());
        metadata.put("rowCount", rowCount);
        metadata.put("artifactRef", report.getArtifactRef());
        metadata.put("artifactHash", report.getArtifactHash());
        metadata.put("payloadHash", payloadHash);
        metadata.put("idempotencyKey", idempotencyKey);

        writeAudit(tenantId, userId, "ROPA_EVIDENCE_ARTIFACT_STORED", report.getId(), payloadHash, metadata);

        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("actorUserId", userId != null ? userId.toString() : null);
        payload.put("reportExportId", report.getId().toString());
        payload.put("reportType", report.getReportType().name());
        payload.put("rowCount", rowCount);
        payload.put("artifactRef", report.getArtifactRef());
        payload.put("artifactHash", report.getArtifactHash());
        payload.put("payloadHash", payloadHash);
        payload.put("idempotencyKey", idempotencyKey);
        writeOutbox("ropa.ropa_evidence_artifact_stored", report.getId().toString(), idempotencyKey, payload);
    }
}
