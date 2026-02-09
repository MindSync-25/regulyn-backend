package io.regulyn.scanner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import io.regulyn.scanner.client.EvidenceClient;
import io.regulyn.scanner.dto.RunEvidenceBundleResponse;
import io.regulyn.scanner.model.RemediationTaskEntity;
import io.regulyn.scanner.model.ScanFinding;
import io.regulyn.scanner.model.ScanRun;
import io.regulyn.scanner.model.ScanRunEvidenceRefEntity;
import io.regulyn.scanner.model.ScanSource;
import io.regulyn.scanner.model.ScannedPageEntity;
import io.regulyn.scanner.repository.RemediationTaskRepository;
import io.regulyn.scanner.repository.ScanFindingRepository;
import io.regulyn.scanner.repository.ScanRunEvidenceRefRepository;
import io.regulyn.scanner.repository.ScanRunRepository;
import io.regulyn.scanner.repository.ScanSourceRepository;
import io.regulyn.scanner.repository.ScannedPageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScanRunEvidenceService {

    private final ScanRunRepository scanRunRepository;
    private final ScanSourceRepository scanSourceRepository;
    private final ScanFindingRepository scanFindingRepository;
    private final ScannedPageRepository scannedPageRepository;
    private final RemediationTaskRepository remediationTaskRepository;
    private final ScanRunEvidenceRefRepository scanRunEvidenceRefRepository;
    private final EvidenceClient evidenceClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper canonicalMapper;

    public ScanRunEvidenceService(ScanRunRepository scanRunRepository,
                                 ScanSourceRepository scanSourceRepository,
                                 ScanFindingRepository scanFindingRepository,
                                 ScannedPageRepository scannedPageRepository,
                                 RemediationTaskRepository remediationTaskRepository,
                                 ScanRunEvidenceRefRepository scanRunEvidenceRefRepository,
                                 EvidenceClient evidenceClient,
                                 AuditWriter auditWriter,
                                 OutboxWriter outboxWriter) {
        this.scanRunRepository = scanRunRepository;
        this.scanSourceRepository = scanSourceRepository;
        this.scanFindingRepository = scanFindingRepository;
        this.scannedPageRepository = scannedPageRepository;
        this.remediationTaskRepository = remediationTaskRepository;
        this.scanRunEvidenceRefRepository = scanRunEvidenceRefRepository;
        this.evidenceClient = evidenceClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.canonicalMapper = new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Transactional
    public RunEvidenceBundleResponse createRunEvidenceBundle(UUID runId, UUID actorUserId, String idempotencyKey) {
        UUID tenantId = TenantContextHolder.getTenantId();

        ScanRunEvidenceRefEntity existing = scanRunEvidenceRefRepository.findByTenantIdAndRunId(tenantId, runId)
            .orElse(null);
        if (existing != null) {
            RunEvidenceBundleResponse response = new RunEvidenceBundleResponse();
            response.setRunId(runId);
            response.setBundleArtifactRef(existing.getBundleArtifactRef());
            response.setBundleHash(existing.getBundleHash());
            response.setStatus(RunEvidenceBundleResponse.Status.ALREADY_EXISTS);
            return response;
        }

        ScanRun run = scanRunRepository.findByTenantIdAndRunId(tenantId, runId)
            .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));

        if (!"SUCCEEDED".equals(run.getStatus()) && !"PARTIAL".equals(run.getStatus())) {
            throw new IllegalStateException("Run must be SUCCEEDED or PARTIAL to create bundle");
        }

        ScanSource source = scanSourceRepository.findByTenantIdAndSourceId(tenantId, run.getSourceId())
            .orElseThrow(() -> new IllegalArgumentException("Source not found: " + run.getSourceId()));

        List<ScannedPageEntity> pages = scannedPageRepository.findByTenantIdAndRunId(tenantId, runId);
        List<ScanFinding> findings = scanFindingRepository.findByTenantIdAndRunIdOrderByCreatedAtDesc(tenantId, runId);
        List<RemediationTaskEntity> tasks = remediationTaskRepository.findByTenantIdAndRunId(tenantId, runId);

        Map<String, Object> bundlePayload = buildBundlePayload(run, source, pages, findings, tasks);
        String bundleHash = sha256(canonicalJson(bundlePayload));

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Tenant-ID", tenantId.toString());
        headers.put("X-User-ID", actorUserId.toString());
        if (idempotencyKey != null) {
            headers.put("X-Idempotency-Key", idempotencyKey);
        }

        UUID evidenceId = evidenceClient.createEvidence("SCAN_RUN_EVIDENCE_SNAPSHOT", bundlePayload, headers);
        UUID bundleId = evidenceClient.createBundle("SCAN_RUN_EVIDENCE_BUNDLE", evidenceId, headers);

        ScanRunEvidenceRefEntity refEntity = new ScanRunEvidenceRefEntity();
        refEntity.setTenantId(tenantId);
        refEntity.setRunId(runId);
        refEntity.setBundleArtifactRef(bundleId.toString());
        refEntity.setBundleHash(bundleHash);
        scanRunEvidenceRefRepository.save(refEntity);

        Map<String, Object> auditPayload = new HashMap<>();
        auditPayload.put("runId", runId);
        auditPayload.put("sourceId", run.getSourceId());
        auditPayload.put("bundleArtifactRef", bundleId.toString());
        auditPayload.put("bundleHash", bundleHash);
        auditPayload.put("taskProofCount", countTaskProofs(tasks));
        auditPayload.put("findingCount", findings.size());
        auditPayload.put("pageCount", pages.size());

        auditWriter.write(com.regulyn.common.audit.AuditEvent.builder()
            .tenantId(tenantId)
            .action("scanner.scan_evidence_bundle_created")
            .entityType("SCAN_RUN")
            .entityId(runId.toString())
            .payloadHash("N/A")
            .build());

        EventEnvelopeV1 outboxEvent = EventFactory.create(
            "scanner.scan_evidence_bundle_created",
            "scanner-service",
            "SCAN_RUN",
            runId.toString(),
            auditPayload
        );
        outboxWriter.write(outboxEvent);

        RunEvidenceBundleResponse response = new RunEvidenceBundleResponse();
        response.setRunId(runId);
        response.setBundleArtifactRef(bundleId.toString());
        response.setBundleHash(bundleHash);
        response.setStatus(RunEvidenceBundleResponse.Status.CREATED);
        return response;
    }

    private Map<String, Object> buildBundlePayload(ScanRun run,
                                                   ScanSource source,
                                                   List<ScannedPageEntity> pages,
                                                   List<ScanFinding> findings,
                                                   List<RemediationTaskEntity> tasks) {
        Map<String, Object> payload = new HashMap<>();

        Map<String, Object> runMeta = new HashMap<>();
        runMeta.put("runId", run.getRunId().toString());
        runMeta.put("sourceId", run.getSourceId().toString());
        runMeta.put("scanMode", run.getScanMode());
        runMeta.put("status", run.getStatus());
        runMeta.put("queuedAt", toIso(run.getQueuedAt()));
        runMeta.put("startedAt", toIso(run.getStartedAt()));
        runMeta.put("finishedAt", toIso(run.getFinishedAt()));
        runMeta.put("resultHash", run.getResultHash());
        runMeta.put("sourceMetadata", source.getMetadata());
        payload.put("run", runMeta);

        List<Map<String, Object>> pageSummaries = pages.stream().map(page -> {
            Map<String, Object> summary = new HashMap<>();
            summary.put("url", page.getUrl());
            summary.put("urlHash", page.getUrlHash());
            summary.put("depth", page.getDepth());
            summary.put("httpStatus", page.getHttpStatus());
            summary.put("title", page.getTitle());
            summary.put("cookieCount", page.getCookieCount());
            summary.put("formCount", page.getFormCount());
            summary.put("trackerCount", page.getTrackerCount());
            summary.put("hasPiiFormFields", page.getHasPiiFormFields());
            summary.put("pageSummaryHash", page.getPageSummaryHash());
            return summary;
        }).collect(Collectors.toList());
        payload.put("pages", pageSummaries);

        List<Map<String, Object>> findingSummaries = findings.stream().map(finding -> {
            Map<String, Object> summary = new HashMap<>();
            summary.put("findingId", finding.getFindingId().toString());
            summary.put("findingFingerprint", finding.getFindingFingerprint());
            summary.put("findingType", finding.getFindingType());
            summary.put("entityType", finding.getEntityType());
            summary.put("subjectId", finding.getSubjectId() != null ? finding.getSubjectId().toString() : null);
            summary.put("fieldName", finding.getFieldName());
            summary.put("riskLevel", finding.getRiskLevel());
            summary.put("normalizedSubject", finding.getNormalizedSubject());
            summary.put("keyAttributes", finding.getKeyAttributes());
            return summary;
        }).collect(Collectors.toList());
        payload.put("findings", findingSummaries);

        List<Map<String, Object>> taskSummaries = tasks.stream().map(task -> {
            Map<String, Object> summary = new HashMap<>();
            summary.put("taskId", task.getTaskId().toString());
            summary.put("findingFingerprint", task.getFindingFingerprint());
            summary.put("status", task.getStatus().name());
            summary.put("severity", task.getSeverity().name());
            summary.put("createdAt", toIso(task.getCreatedAt()));
            summary.put("updatedAt", toIso(task.getUpdatedAt()));
            summary.put("closedAt", toIso(task.getClosedAt()));
            summary.put("closureNotesHash", task.getClosureNotesHash());
            summary.put("evidenceArtifactRef", task.getEvidenceArtifactRef());
            return summary;
        }).collect(Collectors.toList());
        payload.put("tasks", taskSummaries);

        List<String> closureProofs = tasks.stream()
            .filter(task -> task.getEvidenceArtifactRef() != null && !task.getEvidenceArtifactRef().isBlank())
            .map(RemediationTaskEntity::getEvidenceArtifactRef)
            .collect(Collectors.toList());
        payload.put("closureProofs", closureProofs);

        payload.put("generatedAt", Instant.now().toString());

        return payload;
    }

    private int countTaskProofs(List<RemediationTaskEntity> tasks) {
        int count = 0;
        for (RemediationTaskEntity task : tasks) {
            if (task.getEvidenceArtifactRef() != null && !task.getEvidenceArtifactRef().isBlank()) {
                count++;
            }
        }
        return count;
    }

    private String toIso(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    private String canonicalJson(Object payload) {
        try {
            return canonicalMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String part = Integer.toHexString(0xff & b);
                if (part.length() == 1) {
                    hex.append('0');
                }
                hex.append(part);
            }
            return hex.toString();
        } catch (Exception ex) {
            return null;
        }
    }
}
