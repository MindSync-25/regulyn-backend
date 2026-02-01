package io.regulyn.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import io.regulyn.connector.client.EvidenceClient;
import io.regulyn.connector.dto.CreateExportRequest;
import io.regulyn.connector.dto.ExportResponse;
import io.regulyn.connector.model.ConnectorExport;
import io.regulyn.connector.model.ConnectorJob;
import io.regulyn.connector.model.ConnectorJobLog;
import io.regulyn.connector.repository.ConnectorExportRepository;
import io.regulyn.connector.repository.ConnectorJobLogRepository;
import io.regulyn.connector.repository.ConnectorJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final ConnectorJobRepository jobRepository;
    private final ConnectorJobLogRepository jobLogRepository;
    private final ConnectorExportRepository exportRepository;
    private final EvidenceClient evidenceClient;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public ExportService(ConnectorJobRepository jobRepository,
                        ConnectorJobLogRepository jobLogRepository,
                        ConnectorExportRepository exportRepository,
                        EvidenceClient evidenceClient,
                        OutboxWriter outboxWriter,
                        ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.jobLogRepository = jobLogRepository;
        this.exportRepository = exportRepository;
        this.evidenceClient = evidenceClient;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExportResponse createExport(CreateExportRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        try {
            // Build snapshot
            Map<String, Object> snapshot = buildJobSnapshot(tenantId, request);

            // Create evidence
            UUID evidenceId = evidenceClient.createEvidence(snapshot);

            // Create bundle
            Map<String, Object> bundleData = new HashMap<>();
            bundleData.put("evidenceIds", List.of(evidenceId.toString()));
            bundleData.put("bundleType", "AUDIT_EXPORT");
            bundleData.put("referenceType", "PERIOD");
            bundleData.put("referenceId", "connector-jobs:" + 
                    (request.getPeriodFrom() != null ? request.getPeriodFrom() : "all") + ":" +
                    (request.getPeriodTo() != null ? request.getPeriodTo() : "now"));

            UUID bundleId = evidenceClient.createBundle(bundleData);

            // Create export
            UUID evidenceExportId = evidenceClient.createExport(bundleId);

            // Store export record
            ConnectorExport export = new ConnectorExport();
            export.setTenantId(tenantId);
            export.setBundleId(bundleId);
            export.setEvidenceExportId(evidenceExportId);
            export = exportRepository.save(export);

            log.info("Created connector export {} for tenant {}", export.getExportId(), tenantId);

            // Emit event
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("exportId", export.getExportId().toString());
            eventPayload.put("bundleId", bundleId.toString());
            eventPayload.put("evidenceExportId", evidenceExportId.toString());

            EventEnvelopeV1 event = EventFactory.create(
                    "connector.export_created",
                    "connector-service",
                    "connector_export",
                    export.getExportId().toString(),
                    eventPayload
            );
            outboxWriter.write(event);

            ExportResponse response = new ExportResponse();
            response.setBundleId(bundleId);
            response.setExportId(export.getExportId());
            response.setDownloadPath("/connector/exports/" + export.getExportId() + "/download");

            return response;

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating export for tenant {}", tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create export", e);
        }
    }

    public byte[] downloadExport(UUID exportId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        try {
            ConnectorExport export = exportRepository.findByTenantIdAndExportId(tenantId, exportId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Export not found"));

            return evidenceClient.downloadExport(export.getEvidenceExportId());

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error downloading export {} for tenant {}", exportId, tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to download export", e);
        }
    }

    private Map<String, Object> buildJobSnapshot(UUID tenantId, CreateExportRequest request) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("type", "CONNECTOR_JOB_SNAPSHOT");
        snapshot.put("generatedAt", Instant.now().toString());
        snapshot.put("tenantId", tenantId.toString());

        if (request.getPeriodFrom() != null) {
            snapshot.put("periodFrom", request.getPeriodFrom().toString());
        }
        if (request.getPeriodTo() != null) {
            snapshot.put("periodTo", request.getPeriodTo().toString());
        }

        snapshot.put("filters", request.getFilters());

        // Get jobs
        List<ConnectorJob> jobs = jobRepository.findByTenantIdOrderByQueuedAtDesc(tenantId);

        // Filter by period if specified
        if (request.getPeriodFrom() != null || request.getPeriodTo() != null) {
            jobs = jobs.stream()
                    .filter(job -> {
                        if (request.getPeriodFrom() != null && job.getQueuedAt().isBefore(request.getPeriodFrom())) {
                            return false;
                        }
                        if (request.getPeriodTo() != null && job.getQueuedAt().isAfter(request.getPeriodTo())) {
                            return false;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
        }

        // Convert jobs to maps
        List<Map<String, Object>> jobMaps = jobs.stream()
                .map(this::jobToMap)
                .collect(Collectors.toList());

        snapshot.put("jobs", jobMaps);
        snapshot.put("jobCount", jobs.size());

        // Compute snapshot hash
        String snapshotHash = computeHash(snapshot);
        snapshot.put("snapshotHash", snapshotHash);

        return snapshot;
    }

    private Map<String, Object> jobToMap(ConnectorJob job) {
        Map<String, Object> map = new HashMap<>();
        map.put("jobId", job.getJobId().toString());
        map.put("jobType", job.getJobType());
        map.put("status", job.getStatus());
        map.put("connectorId", job.getConnectorId().toString());
        map.put("targetId", job.getTargetId().toString());
        map.put("subjectId", job.getSubjectId().toString());
        map.put("subjectType", job.getSubjectType());
        map.put("requestRef", job.getRequestRef());
        map.put("queuedAt", job.getQueuedAt().toString());
        if (job.getStartedAt() != null) map.put("startedAt", job.getStartedAt().toString());
        if (job.getFinishedAt() != null) map.put("finishedAt", job.getFinishedAt().toString());
        map.put("resultHash", job.getResultHash());

        // Get logs for this job
        List<ConnectorJobLog> logs = jobLogRepository.findByTenantIdAndJobIdOrderByCreatedAtAsc(job.getTenantId(), job.getJobId());
        List<Map<String, Object>> logMaps = logs.stream()
                .map(log -> {
                    Map<String, Object> logMap = new HashMap<>();
                    logMap.put("step", log.getStep());
                    logMap.put("status", log.getStatus());
                    logMap.put("createdAt", log.getCreatedAt().toString());
                    return logMap;
                })
                .collect(Collectors.toList());
        map.put("logs", logMaps);

        return map;
    }

    private String computeHash(Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to compute hash", e);
            return "error-computing-hash";
        }
    }
}
