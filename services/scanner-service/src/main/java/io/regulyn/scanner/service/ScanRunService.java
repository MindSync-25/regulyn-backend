package io.regulyn.scanner.service;

import com.regulyn.common.audit.AuditWriter;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import io.regulyn.scanner.adapter.ScanAdapter;
import io.regulyn.scanner.adapter.model.Finding;
import io.regulyn.scanner.dto.CreateScanRunRequest;
import io.regulyn.scanner.dto.ScanRunResponse;
import io.regulyn.scanner.model.ScanFinding;
import io.regulyn.scanner.model.ScanRun;
import io.regulyn.scanner.model.ScanSource;
import io.regulyn.scanner.repository.ScanFindingRepository;
import io.regulyn.scanner.repository.ScanRunRepository;
import io.regulyn.scanner.website.WebsiteCrawlResult;
import io.regulyn.scanner.website.WebsiteScanAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScanRunService {

    private static final Logger log = LoggerFactory.getLogger(ScanRunService.class);

    private final ScanRunRepository scanRunRepository;
    private final ScanFindingRepository scanFindingRepository;
    private final ScanSourceService scanSourceService;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final Map<String, ScanAdapter> scanAdapters;

    public ScanRunService(ScanRunRepository scanRunRepository,
                          ScanFindingRepository scanFindingRepository,
                          ScanSourceService scanSourceService,
                          AuditWriter auditWriter,
                          OutboxWriter outboxWriter,
                          Map<String, ScanAdapter> scanAdapters) {
        this.scanRunRepository = scanRunRepository;
        this.scanFindingRepository = scanFindingRepository;
        this.scanSourceService = scanSourceService;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.scanAdapters = scanAdapters;
    }

    @Transactional
    public ScanRunResponse createRun(CreateScanRunRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        // Validate source exists and is active
        ScanSource source = scanSourceService.getSource(request.getSourceId());
        if (!"ACTIVE".equals(source.getStatus())) {
            throw new IllegalArgumentException("Cannot create run for disabled source");
        }

        ScanRun run = new ScanRun();
        run.setTenantId(tenantId);
        run.setSourceId(request.getSourceId());
        run.setScanMode(request.getScanMode().name());
        run.setStatus("QUEUED");
        run.setSinceAt(request.getSince());
        run.setRequestRef(request.getRequestRef());

        run = scanRunRepository.save(run);

        // Audit
        auditWriter.write(com.regulyn.common.audit.AuditEvent.builder()
                .tenantId(tenantId)
                .action("scanner.run_created")
                .entityType("SCAN_RUN")
                .entityId(run.getRunId().toString())                .payloadHash("N/A")                .payloadHash("N/A")
                .build());

        // Outbox
        EventEnvelopeV1 event = EventFactory.create(
                "scanner.run_created",
                "scanner-service",
                "SCAN_RUN",
                run.getRunId().toString(),
                run
        );
        outboxWriter.write(event);

        return toResponse(run);
    }

    @Transactional
    public ScanRunResponse executeRun(UUID runId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        ScanRun run = scanRunRepository.findByTenantIdAndRunId(tenantId, runId)
            .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));

        if (!"QUEUED".equals(run.getStatus())) {
            throw new IllegalStateException("Run is not in QUEUED status: " + run.getStatus());
        }

        // Update to RUNNING
        run.setStatus("RUNNING");
        run.setStartedAt(Instant.now());
        run = scanRunRepository.save(run);

        // Audit
        auditWriter.write(com.regulyn.common.audit.AuditEvent.builder()
                .tenantId(tenantId)
                .action("scanner.run_started")
                .entityType("SCAN_RUN")
                .entityId(run.getRunId().toString())                .payloadHash("N/A")                .build());

        // Outbox
        EventEnvelopeV1 startEvent = EventFactory.create(
                "scanner.run_started",
                "scanner-service",
                "SCAN_RUN",
                run.getRunId().toString(),
                run
        );
        outboxWriter.write(startEvent);

        try {
            // Get source and adapter
            ScanSource source = scanSourceService.getSource(run.getSourceId());
            ScanAdapter adapter = scanAdapters.get(source.getSourceType());
            if (adapter == null) {
                throw new IllegalStateException("No adapter found for source type: " + source.getSourceType());
            }

            // Execute scan based on mode
            List<Finding> findings;
            String scanMode = run.getScanMode();
            WebsiteCrawlResult websiteCrawlResult = null;
            
            if (adapter instanceof WebsiteScanAdapter websiteScanAdapter) {
                websiteCrawlResult = websiteScanAdapter.crawl(source, run.getSinceAt(), tenantId, run.getRunId(), scanMode);
                if (websiteCrawlResult.isFailed()) {
                    throw new IllegalStateException(websiteCrawlResult.getFailureReason());
                }
                findings = websiteCrawlResult.getFindings();
            } else {
                if ("INVENTORY".equals(scanMode)) {
                    findings = adapter.runInventory(source, run.getSinceAt());
                } else if ("RETENTION_CANDIDATES".equals(scanMode)) {
                    findings = adapter.runRetentionCandidates(source, run.getSinceAt());
                } else if ("BOTH".equals(scanMode)) {
                    List<Finding> inventory = adapter.runInventory(source, run.getSinceAt());
                    List<Finding> retention = adapter.runRetentionCandidates(source, run.getSinceAt());
                    inventory.addAll(retention);
                    findings = inventory;
                } else {
                    throw new IllegalStateException("Unknown scan mode: " + scanMode);
                }
            }

            // Save findings
            int savedCount = 0;
            List<Finding> persistedFindings = new ArrayList<>();
            for (Finding finding : findings) {
                ScanFinding entity = new ScanFinding();
                entity.setTenantId(tenantId);
                entity.setRunId(run.getRunId());
                entity.setFindingType(finding.getFindingType());
                entity.setEntityType(finding.getEntityType());
                entity.setSubjectId(finding.getSubjectId());
                entity.setFieldName(finding.getFieldName());
                entity.setDataCategory(finding.getDataCategory());
                entity.setRiskLevel(finding.getRiskLevel());
                entity.setConfidence(finding.getConfidence());
                entity.setFindingFingerprint(finding.getFindingFingerprint());
                entity.setFindingFingerprintVersion(
                    finding.getFindingFingerprintVersion() != null ? finding.getFindingFingerprintVersion() : 1
                );
                entity.setNormalizedSubject(finding.getNormalizedSubject());
                if (finding.getKeyAttributes() != null) {
                    entity.setKeyAttributes(finding.getKeyAttributes());
                }
                if (finding.getDetails() != null) {
                    entity.setDetails(finding.getDetails());
                }
                try {
                    scanFindingRepository.saveAndFlush(entity);
                    savedCount++;
                    persistedFindings.add(finding);
                } catch (DataIntegrityViolationException ex) {
                    log.debug("Duplicate finding ignored for run {}", run.getRunId());
                }
            }

            // Update run status
            boolean partial = websiteCrawlResult != null && websiteCrawlResult.isPartial();
            run.setStatus(partial ? "PARTIAL" : "SUCCEEDED");
            run.setFinishedAt(Instant.now());
            run.setFindingsCount(savedCount);
            run.setResultHash(computeResultHash(persistedFindings));
            if (partial) {
                run.setErrorMessage(websiteCrawlResult.getPartialReason());
            }
            run = scanRunRepository.save(run);

                // Audit + Outbox for run completion
                if (partial) {
                auditWriter.write(com.regulyn.common.audit.AuditEvent.builder()
                    .tenantId(tenantId)
                    .action("scanner.run_partial")
                    .entityType("SCAN_RUN")
                    .entityId(run.getRunId().toString())
                    .payloadHash("N/A")
                    .build());

                EventEnvelopeV1 partialEvent = EventFactory.create(
                    "scanner.run_partial",
                    "scanner-service",
                    "SCAN_RUN",
                    run.getRunId().toString(),
                    Map.of(
                        "reason", websiteCrawlResult.getPartialReason(),
                        "fetchedPages", websiteCrawlResult.getFetchedPages(),
                        "failures", websiteCrawlResult.getFailures()
                    )
                );
                outboxWriter.write(partialEvent);
                } else {
                auditWriter.write(com.regulyn.common.audit.AuditEvent.builder()
                    .tenantId(tenantId)
                    .action("scanner.run_succeeded")
                    .entityType("SCAN_RUN")
                    .entityId(run.getRunId().toString())
                    .payloadHash("N/A")
                    .build());

                EventEnvelopeV1 succeededEvent = EventFactory.create(
                    "scanner.run_succeeded",
                    "scanner-service",
                    "SCAN_RUN",
                    run.getRunId().toString(),
                    run
                );
                outboxWriter.write(succeededEvent);
                }

                // Findings created event
                EventEnvelopeV1 findingsEvent = EventFactory.create(
                    "scanner.findings_created",
                    "scanner-service",
                    "SCAN_FINDINGS",
                    run.getRunId().toString(),
                    Map.of("findingsCount", savedCount)
                );
                outboxWriter.write(findingsEvent);

                if (savedCount > 0) {
                Map<String, Object> payload = buildFindingDetectedPayload(run.getRunId(), persistedFindings);

                auditWriter.write(com.regulyn.common.audit.AuditEvent.builder()
                    .tenantId(tenantId)
                    .action("scanner.finding_detected")
                    .entityType("SCAN_FINDINGS")
                    .entityId(run.getRunId().toString())
                    .payloadHash("N/A")
                    .build());

                EventEnvelopeV1 findingDetectedEvent = EventFactory.create(
                    "scanner.finding_detected",
                    "scanner-service",
                    "SCAN_FINDINGS",
                    run.getRunId().toString(),
                    payload
                );
                outboxWriter.write(findingDetectedEvent);
                }

        } catch (Exception e) {
            log.error("Scan execution failed for run {}: {}", runId, e.getMessage(), e);

            run.setStatus("FAILED");
            run.setFinishedAt(Instant.now());
            run.setErrorMessage(e.getMessage());
            run = scanRunRepository.save(run);

            // Audit
            auditWriter.write(com.regulyn.common.audit.AuditEvent.builder()
                    .tenantId(tenantId)
                    .action("scanner.run_failed")
                    .entityType("SCAN_RUN")
                    .entityId(run.getRunId().toString())
                    .payloadHash("N/A")
                    .build());

            // Outbox
            EventEnvelopeV1 failedEvent = EventFactory.create(
                    "scanner.run_failed",
                    "scanner-service",
                    "SCAN_RUN",
                    run.getRunId().toString(),
                    Map.of("error", e.getMessage())
            );
            outboxWriter.write(failedEvent);
        }

        return toResponse(run);
    }

    private Map<String, Object> buildFindingDetectedPayload(UUID runId, List<Finding> findings) {
        Map<String, Integer> kindCounts = new HashMap<>();
        List<String> fingerprints = new ArrayList<>();

        for (Finding finding : findings) {
            String kind = null;
            if (finding.getDetails() != null) {
                Object kindValue = finding.getDetails().get("websiteFindingKind");
                if (kindValue != null) {
                    kind = kindValue.toString();
                }
            }
            if (kind == null) {
                kind = finding.getFindingType() != null ? finding.getFindingType() : "UNKNOWN";
            }
            kindCounts.put(kind, kindCounts.getOrDefault(kind, 0) + 1);

            if (finding.getFindingFingerprint() != null && fingerprints.size() < 50) {
                fingerprints.add(finding.getFindingFingerprint());
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("runId", runId.toString());
        payload.put("count", findings.size());
        payload.put("fingerprints", fingerprints);
        payload.put("kindCounts", kindCounts);
        return payload;
    }

    @Transactional(readOnly = true)
    public ScanRunResponse getRun(UUID runId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ScanRun run = scanRunRepository.findByTenantIdAndRunId(tenantId, runId)
            .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
        return toResponse(run);
    }

    @Transactional(readOnly = true)
    public Page<ScanRunResponse> listRuns(String status, UUID sourceId, Pageable pageable) {
        UUID tenantId = TenantContextHolder.getTenantId();

        Page<ScanRun> runs;
        if (status != null && sourceId != null) {
            runs = scanRunRepository.findByTenantIdAndSourceIdOrderByQueuedAtDesc(tenantId, sourceId, pageable);
        } else if (status != null) {
            runs = scanRunRepository.findByTenantIdAndStatusOrderByQueuedAtDesc(tenantId, status, pageable);
        } else if (sourceId != null) {
            runs = scanRunRepository.findByTenantIdAndSourceIdOrderByQueuedAtDesc(tenantId, sourceId, pageable);
        } else {
            runs = scanRunRepository.findByTenantIdOrderByQueuedAtDesc(tenantId, pageable);
        }

        return runs.map(this::toResponse);
    }

    private String computeResultHash(List<Finding> findings) {
        try {
            // Create canonical representation
            String canonical = findings.stream()
                .map(f -> f.getFindingType() + ":" + f.getEntityType() + ":" + f.getRiskLevel())
                .sorted()
                .collect(Collectors.joining("|"));

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.warn("Failed to compute result hash", e);
            return null;
        }
    }

    private ScanRunResponse toResponse(ScanRun run) {
        ScanRunResponse response = new ScanRunResponse();
        response.setRunId(run.getRunId());
        response.setSourceId(run.getSourceId());
        response.setScanMode(run.getScanMode());
        response.setStatus(run.getStatus());
        response.setSinceAt(run.getSinceAt());
        response.setRequestRef(run.getRequestRef());
        response.setQueuedAt(run.getQueuedAt());
        response.setStartedAt(run.getStartedAt());
        response.setFinishedAt(run.getFinishedAt());
        response.setFindingsCount(run.getFindingsCount());
        response.setResultHash(run.getResultHash());
        response.setErrorMessage(run.getErrorMessage());
        return response;
    }
}
