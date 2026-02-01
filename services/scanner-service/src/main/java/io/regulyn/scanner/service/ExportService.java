package io.regulyn.scanner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import io.regulyn.scanner.client.EvidenceClient;
import io.regulyn.scanner.dto.CreateScanExportRequest;
import io.regulyn.scanner.dto.ScanExportResponse;
import io.regulyn.scanner.model.ScanFinding;
import io.regulyn.scanner.model.ScanRun;
import io.regulyn.scanner.model.ScannerExport;
import io.regulyn.scanner.repository.ScanFindingRepository;
import io.regulyn.scanner.repository.ScanRunRepository;
import io.regulyn.scanner.repository.ScannerExportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExportService {

    private final ScanRunRepository scanRunRepository;
    private final ScanFindingRepository scanFindingRepository;
    private final ScannerExportRepository scannerExportRepository;
    private final EvidenceClient evidenceClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public ExportService(ScanRunRepository scanRunRepository,
                         ScanFindingRepository scanFindingRepository,
                         ScannerExportRepository scannerExportRepository,
                         EvidenceClient evidenceClient,
                         AuditWriter auditWriter,
                         OutboxWriter outboxWriter,
                         ObjectMapper objectMapper) {
        this.scanRunRepository = scanRunRepository;
        this.scanFindingRepository = scanFindingRepository;
        this.scannerExportRepository = scannerExportRepository;
        this.evidenceClient = evidenceClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ScanExportResponse createExport(CreateScanExportRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        // Get runs in period
        List<ScanRun> runs = scanRunRepository.findByTenantIdOrderByQueuedAtDesc(tenantId, org.springframework.data.domain.Pageable.unpaged())
            .getContent();

        // Filter by period
        Instant periodFrom = request.getPeriodFrom();
        Instant periodTo = request.getPeriodTo();

        if (periodFrom != null || periodTo != null) {
            runs = runs.stream()
                .filter(run -> {
                    Instant queuedAt = run.getQueuedAt();
                    if (periodFrom != null && queuedAt.isBefore(periodFrom)) return false;
                    if (periodTo != null && queuedAt.isAfter(periodTo)) return false;
                    return true;
                })
                .collect(Collectors.toList());
        }

        // Build export payload
        Map<String, Object> exportData = new HashMap<>();
        exportData.put("runs", runs);

        // Get findings for each run
        List<Map<String, Object>> allFindings = new ArrayList<>();
        for (ScanRun run : runs) {
            List<ScanFinding> findings = scanFindingRepository.findByTenantIdAndRunIdOrderByCreatedAtDesc(tenantId, run.getRunId());
            for (ScanFinding finding : findings) {
                Map<String, Object> findingMap = new HashMap<>();
                findingMap.put("runId", finding.getRunId());
                findingMap.put("findingType", finding.getFindingType());
                findingMap.put("entityType", finding.getEntityType());
                findingMap.put("subjectId", finding.getSubjectId());
                findingMap.put("riskLevel", finding.getRiskLevel());
                findingMap.put("confidence", finding.getConfidence());
                findingMap.put("createdAt", finding.getCreatedAt());
                allFindings.add(findingMap);
            }
        }
        exportData.put("findings", allFindings);
        exportData.put("totalRuns", runs.size());
        exportData.put("totalFindings", allFindings.size());

        // Create evidence
        UUID evidenceId = evidenceClient.createEvidence("SCAN_REPORT_SNAPSHOT", exportData);

        // Create bundle
        UUID bundleId = evidenceClient.createBundle("AUDIT_EXPORT", evidenceId);

        // Create export
        UUID evidenceExportId = evidenceClient.createExport(bundleId);

        // Save export record
        ScannerExport export = new ScannerExport();
        export.setTenantId(tenantId);
        export.setBundleId(bundleId);
        export.setEvidenceExportId(evidenceExportId);
        export = scannerExportRepository.save(export);

        // Audit
        auditWriter.write(com.regulyn.common.audit.AuditEvent.builder()
                .tenantId(tenantId)
                .action("scanner.export_created")
                .entityType("SCANNER_EXPORT")
                .entityId(export.getExportId().toString())
                .payloadHash("N/A")
                .build());

        // Outbox
        EventEnvelopeV1 event = EventFactory.create(
                "scanner.export_created",
                "scanner-service",
                "SCANNER_EXPORT",
                export.getExportId().toString(),
                Map.of("bundleId", bundleId, "exportId", evidenceExportId)
        );
        outboxWriter.write(event);

        ScanExportResponse response = new ScanExportResponse();
        response.setBundleId(bundleId);
        response.setExportId(export.getExportId());
        response.setDownloadPath("/scanner/exports/" + export.getExportId() + "/download");
        return response;
    }

    @Transactional(readOnly = true)
    public byte[] downloadExport(UUID exportId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        ScannerExport export = scannerExportRepository.findByTenantIdAndExportId(tenantId, exportId)
            .orElseThrow(() -> new IllegalArgumentException("Export not found: " + exportId));

        return evidenceClient.downloadExport(export.getEvidenceExportId());
    }
}
