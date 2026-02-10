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
import com.regulyn.ropa.model.ReportStatus;
import com.regulyn.ropa.model.ReportType;
import com.regulyn.ropa.model.RopaReportExportEntity;
import com.regulyn.ropa.repository.RopaReportExportRepository;
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

@Service
public class CrossBorderReportExportService {

    private static final Logger log = LoggerFactory.getLogger(CrossBorderReportExportService.class);

    private final RopaReportExportRepository reportExportRepository;
    private final CrossBorderTransferQueryService queryService;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final EvidenceReportingClient evidenceReportingClient;
    private final ObjectMapper objectMapper;

    public CrossBorderReportExportService(RopaReportExportRepository reportExportRepository,
                                          CrossBorderTransferQueryService queryService,
                                          AuditWriter auditWriter,
                                          OutboxWriter outboxWriter,
                                          EvidenceReportingClient evidenceReportingClient,
                                          ObjectMapper objectMapper) {
        this.reportExportRepository = reportExportRepository;
        this.queryService = queryService;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.evidenceReportingClient = evidenceReportingClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public CrossBorderReportExportResponse exportReport(UUID tenantId, UUID userId, String idempotencyKey,
                                                        CrossBorderReportExportRequest request) {
        CrossBorderReportFilters filters = request != null && request.getFilters() != null
            ? request.getFilters()
            : new CrossBorderReportFilters();

        String payloadHash = computeRequestHash(filters);

        Optional<RopaReportExportEntity> existing = reportExportRepository
            .findByTenantIdAndReportTypeAndIdempotencyKey(tenantId, ReportType.CROSS_BORDER_REPORT, idempotencyKey);
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
            List<CrossBorderReportRow> rows = buildRows(tenantId, filters);
            return buildResponse(existing.get(), rows);
        }

        RopaReportExportEntity report = new RopaReportExportEntity();
        report.setTenantId(tenantId);
        report.setReportType(ReportType.CROSS_BORDER_REPORT);
        report.setStatus(ReportStatus.REQUESTED);
        report.setRequestedBy(userId);
        report.setRequestedAt(Instant.now());
        report.setIdempotencyKey(idempotencyKey);
        report = reportExportRepository.save(report);

        try {
            List<CrossBorderReportRow> rows = buildRows(tenantId, filters);
            Map<String, Object> evidencePayload = buildEvidencePayload(report, filters, rows);
            report.setPayloadHash(payloadHash);
            report = reportExportRepository.save(report);

            writeAudit(tenantId, userId, "CROSS_BORDER_REPORT_EXPORT_REQUESTED", report.getId(), payloadHash,
                buildExportMetadata(report, filters, 0, payloadHash));
            writeOutbox("ropa.cross_border_report_export_requested", report.getId().toString(), idempotencyKey,
                buildExportPayload(tenantId, userId, report, filters, 0, payloadHash));

            EvidenceReportingClient.EvidenceArtifactResponse artifact = evidenceReportingClient.createArtifact(
                tenantId,
                userId,
                idempotencyKey,
                "ROPA_CROSS_BORDER_REPORT",
                "ropa-cross-border-report-" + tenantId + "-" + report.getId() + ".json",
                "application/json",
                evidencePayload
            );

            report.setStatus(ReportStatus.CREATED);
            report.setCreatedAt(Instant.now());
            report.setArtifactRef(artifact.artifactRef());
            report.setArtifactHash(artifact.artifactHash());
            report = reportExportRepository.save(report);

            writeAudit(tenantId, userId, "CROSS_BORDER_REPORT_EXPORT_CREATED", report.getId(), payloadHash,
                buildExportMetadata(report, filters, rows.size(), payloadHash));
            writeOutbox("ropa.cross_border_report_export_created", report.getId().toString(), idempotencyKey,
                buildExportPayload(tenantId, userId, report, filters, rows.size(), payloadHash));

            writeEvidenceStoredEvents(tenantId, userId, idempotencyKey, report, rows.size(), payloadHash);

            return buildResponse(report, rows);
        } catch (Exception e) {
            report.setStatus(ReportStatus.FAILED);
            report.setErrorCode("EXPORT_FAILED");
            report.setErrorMessage(e.getMessage());
            reportExportRepository.save(report);

            writeAudit(tenantId, userId, "CROSS_BORDER_REPORT_EXPORT_FAILED", report.getId(),
                report.getPayloadHash() != null ? report.getPayloadHash() : "", buildFailureMetadata(report));

            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "EVIDENCE_SERVICE_UNAVAILABLE");
        }
    }

    private CrossBorderReportExportResponse buildResponse(RopaReportExportEntity report, List<CrossBorderReportRow> rows) {
        CrossBorderReportExportResponse response = new CrossBorderReportExportResponse();
        response.setReportExportId(report.getId());
        response.setStatus(report.getStatus());
        response.setRowCount(rows.size());
        Instant generatedAt = report.getCreatedAt() != null ? report.getCreatedAt() : Instant.now();
        response.setGeneratedAt(generatedAt.truncatedTo(ChronoUnit.MICROS));
        response.setRows(rows);
        response.setArtifactRef(report.getArtifactRef());
        response.setArtifactHash(report.getArtifactHash());
        return response;
    }

    private List<CrossBorderReportRow> buildRows(UUID tenantId, CrossBorderReportFilters filters) {
        CrossBorderTransferFilters transferFilters = new CrossBorderTransferFilters();
        transferFilters.setVendorId(filters.getVendorId());
        transferFilters.setSystemId(filters.getSystemId());
        transferFilters.setActivityId(filters.getActivityId());
        transferFilters.setSourceRegion(filters.getSourceRegion());
        transferFilters.setDestinationRegion(filters.getDestinationRegion());
        transferFilters.setDataCategoryId(filters.getDataCategoryId());
        transferFilters.setPurposeVersionId(filters.getPurposeVersionId());

        List<CrossBorderTransferResponse> transfers = queryService.queryTransfers(tenantId, transferFilters);
        List<CrossBorderReportRow> rows = new ArrayList<>();
        for (CrossBorderTransferResponse transfer : transfers) {
            CrossBorderReportRow row = new CrossBorderReportRow();
            row.setTransferId(transfer.getTransferId());
            row.setSystemId(transfer.getSystemId());
            row.setActivityId(transfer.getActivityId());
            row.setVendorId(transfer.getVendorId());
            row.setSourceRegion(transfer.getSourceRegion());
            row.setDestinationRegion(transfer.getDestinationRegion());
            row.setTransferMechanism(transfer.getTransferMechanism());
            row.setLegalBasis(transfer.getLegalBasis());
            row.setFrequency(transfer.getFrequency());
            row.setStartedAt(transfer.getStartedAt());
            row.setEndedAt(transfer.getEndedAt());
            if (transfer.getDataCategoryIds() != null) {
                List<UUID> dataCategoryIds = new ArrayList<>(transfer.getDataCategoryIds());
                dataCategoryIds.sort(UUID::compareTo);
                row.setDataCategoryIds(dataCategoryIds);
            } else {
                row.setDataCategoryIds(null);
            }
            if (transfer.getPurposeVersionIds() != null) {
                List<UUID> purposeVersionIds = new ArrayList<>(transfer.getPurposeVersionIds());
                purposeVersionIds.sort(UUID::compareTo);
                row.setPurposeVersionIds(purposeVersionIds);
            } else {
                row.setPurposeVersionIds(null);
            }
            rows.add(row);
        }
        rows.sort(Comparator
            .comparing(CrossBorderReportRow::getSystemId, Comparator.nullsLast(UUID::compareTo))
            .thenComparing(CrossBorderReportRow::getActivityId, Comparator.nullsLast(UUID::compareTo))
            .thenComparing(CrossBorderReportRow::getVendorId, Comparator.nullsLast(UUID::compareTo))
            .thenComparing(CrossBorderReportRow::getSourceRegion, Comparator.nullsLast(String::compareTo))
            .thenComparing(CrossBorderReportRow::getDestinationRegion, Comparator.nullsLast(String::compareTo))
            .thenComparing(CrossBorderReportRow::getTransferId, Comparator.nullsLast(UUID::compareTo)));

        return rows;
    }

    private void writeAudit(UUID tenantId, UUID userId, String action, UUID reportId, String payloadHash, ObjectNode metadata) {
        try {
            AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(userId)
                .actorType(AuditEvent.ActorType.USER)
                .action(action)
                .entityType("ROPA_CROSS_BORDER_REPORT_EXPORT")
                .entityId(reportId.toString())
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
            EventEnvelopeV1 event = EventFactory.create(eventType, "ropa-inventory-service", "ropa_cross_border_report",
                entityId, payload, idempotencyKey);
            outboxWriter.write(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", e.getMessage());
        }
    }

    private ObjectNode buildExportMetadata(RopaReportExportEntity report, CrossBorderReportFilters filters, int rowCount, String payloadHash) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("reportExportId", report.getId().toString());
        node.put("reportType", report.getReportType().name());
        node.put("status", report.getStatus().name());
        node.put("rowCount", rowCount);
        if (payloadHash != null) {
            node.put("payloadHash", payloadHash);
        }
        if (filters.getVendorId() != null) {
            node.put("vendorId", filters.getVendorId().toString());
        }
        if (filters.getSystemId() != null) {
            node.put("systemId", filters.getSystemId().toString());
        }
        if (filters.getActivityId() != null) {
            node.put("activityId", filters.getActivityId().toString());
        }
        if (filters.getSourceRegion() != null) {
            node.put("sourceRegion", filters.getSourceRegion());
        }
        if (filters.getDestinationRegion() != null) {
            node.put("destinationRegion", filters.getDestinationRegion());
        }
        if (filters.getDataCategoryId() != null) {
            node.put("dataCategoryId", filters.getDataCategoryId().toString());
        }
        if (filters.getPurposeVersionId() != null) {
            node.put("purposeVersionId", filters.getPurposeVersionId().toString());
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
                                                   CrossBorderReportFilters filters, int rowCount, String payloadHash) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId.toString());
        if (userId != null) {
            payload.put("actorUserId", userId.toString());
        }
        payload.put("reportExportId", report.getId().toString());
        payload.put("reportType", report.getReportType().name());
        payload.put("status", report.getStatus().name());
        payload.put("rowCount", rowCount);
        if (filters.getVendorId() != null) {
            payload.put("vendorId", filters.getVendorId().toString());
        }
        if (filters.getSystemId() != null) {
            payload.put("systemId", filters.getSystemId().toString());
        }
        if (filters.getActivityId() != null) {
            payload.put("activityId", filters.getActivityId().toString());
        }
        if (filters.getSourceRegion() != null) {
            payload.put("sourceRegion", filters.getSourceRegion());
        }
        if (filters.getDestinationRegion() != null) {
            payload.put("destinationRegion", filters.getDestinationRegion());
        }
        if (filters.getDataCategoryId() != null) {
            payload.put("dataCategoryId", filters.getDataCategoryId().toString());
        }
        if (filters.getPurposeVersionId() != null) {
            payload.put("purposeVersionId", filters.getPurposeVersionId().toString());
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
                                                     CrossBorderReportFilters filters,
                                                     List<CrossBorderReportRow> rows) {
        Map<String, Object> filterPayload = new LinkedHashMap<>();
        filterPayload.put("vendorId", filters.getVendorId() != null ? filters.getVendorId().toString() : null);
        filterPayload.put("systemId", filters.getSystemId() != null ? filters.getSystemId().toString() : null);
        filterPayload.put("activityId", filters.getActivityId() != null ? filters.getActivityId().toString() : null);
        filterPayload.put("sourceRegion", filters.getSourceRegion());
        filterPayload.put("destinationRegion", filters.getDestinationRegion());
        filterPayload.put("dataCategoryId", filters.getDataCategoryId() != null ? filters.getDataCategoryId().toString() : null);
        filterPayload.put("purposeVersionId", filters.getPurposeVersionId() != null ? filters.getPurposeVersionId().toString() : null);

        Map<String, Object> payload = new LinkedHashMap<>();
        Instant generatedAt = report.getRequestedAt() != null
            ? report.getRequestedAt()
            : report.getCreatedAt() != null ? report.getCreatedAt() : Instant.now();
        payload.put("generatedAt", generatedAt.truncatedTo(ChronoUnit.MICROS).toString());
        payload.put("reportExportId", report.getId().toString());
        payload.put("reportType", report.getReportType().name());
        payload.put("filters", filterPayload);
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

    private String computeRequestHash(CrossBorderReportFilters filters) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vendorId", filters.getVendorId() != null ? filters.getVendorId().toString() : null);
        payload.put("systemId", filters.getSystemId() != null ? filters.getSystemId().toString() : null);
        payload.put("activityId", filters.getActivityId() != null ? filters.getActivityId().toString() : null);
        payload.put("sourceRegion", filters.getSourceRegion());
        payload.put("destinationRegion", filters.getDestinationRegion());
        payload.put("dataCategoryId", filters.getDataCategoryId() != null ? filters.getDataCategoryId().toString() : null);
        payload.put("purposeVersionId", filters.getPurposeVersionId() != null ? filters.getPurposeVersionId().toString() : null);
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
