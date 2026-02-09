package io.regulyn.scanner.service;

import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import io.regulyn.scanner.dto.TaskGenerationRequest;
import io.regulyn.scanner.dto.TaskGenerationResponse;
import io.regulyn.scanner.model.RemediationTaskEntity;
import io.regulyn.scanner.model.RemediationTaskEventEntity;
import io.regulyn.scanner.model.ScanFinding;
import io.regulyn.scanner.model.ScanRun;
import io.regulyn.scanner.model.ScanSource;
import io.regulyn.scanner.repository.RemediationTaskEventRepository;
import io.regulyn.scanner.repository.RemediationTaskRepository;
import io.regulyn.scanner.repository.ScanFindingRepository;
import io.regulyn.scanner.repository.ScanRunRepository;
import io.regulyn.scanner.repository.ScanSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class TaskGenerationService {

    private static final int EVENT_LIST_LIMIT = 10;

    private static final Set<String> DEFAULT_KINDS = Set.of(
        "INSECURE_FORM_ACTION_HTTP",
        "FORM_PII_DETECTED",
        "TRACKER_DETECTED",
        "UNKNOWN_THIRD_PARTY_ENDPOINT"
    );

    private final ScanRunRepository scanRunRepository;
    private final ScanSourceRepository scanSourceRepository;
    private final ScanFindingRepository scanFindingRepository;
    private final RemediationTaskRepository remediationTaskRepository;
    private final RemediationTaskEventRepository remediationTaskEventRepository;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;

    public TaskGenerationService(ScanRunRepository scanRunRepository,
                                 ScanSourceRepository scanSourceRepository,
                                 ScanFindingRepository scanFindingRepository,
                                 RemediationTaskRepository remediationTaskRepository,
                                 RemediationTaskEventRepository remediationTaskEventRepository,
                                 AuditWriter auditWriter,
                                 OutboxWriter outboxWriter) {
        this.scanRunRepository = scanRunRepository;
        this.scanSourceRepository = scanSourceRepository;
        this.scanFindingRepository = scanFindingRepository;
        this.remediationTaskRepository = remediationTaskRepository;
        this.remediationTaskEventRepository = remediationTaskEventRepository;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public TaskGenerationResponse generateTasksForRun(UUID runId, TaskGenerationRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        ScanRun run = scanRunRepository.findByTenantIdAndRunId(tenantId, runId)
            .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));

        if (!"SUCCEEDED".equals(run.getStatus()) && !"PARTIAL".equals(run.getStatus())) {
            throw new IllegalStateException("Run must be SUCCEEDED or PARTIAL to generate tasks");
        }

        ScanSource source = scanSourceRepository.findByTenantIdAndSourceId(tenantId, run.getSourceId())
            .orElseThrow(() -> new IllegalArgumentException("Source not found: " + run.getSourceId()));

        List<ScanFinding> findings = scanFindingRepository
            .findByTenantIdAndRunIdAndFindingFingerprintIsNotNullOrderByCreatedAtDesc(tenantId, runId);

        Set<String> allowedKinds = resolveKinds(request);
        String primaryHost = hostOf(source.getBaseUrl());
        Set<String> allowedTrackerDomains = extractAllowedTrackerDomains(source.getMetadata());

        List<UUID> createdTaskIds = new ArrayList<>();
        List<UUID> existingTaskIds = new ArrayList<>();
        List<String> createdFingerprints = new ArrayList<>();
        Map<String, Integer> kindCounts = new HashMap<>();
        int createdCount = 0;
        int skippedCount = 0;

        for (ScanFinding finding : findings) {
            String kind = extractKind(finding);
            if (kind == null || !allowedKinds.contains(kind)) {
                continue;
            }

            if (!shouldCreateTaskForKind(kind, finding, primaryHost, allowedTrackerDomains)) {
                skippedCount++;
                continue;
            }

            String fingerprint = finding.getFindingFingerprint();
            if (fingerprint == null || fingerprint.isBlank()) {
                skippedCount++;
                continue;
            }

            Optional<RemediationTaskEntity> existingActive = remediationTaskRepository
                .findFirstByTenantIdAndFindingFingerprintAndStatusIn(
                    tenantId,
                    fingerprint,
                    List.of(RemediationTaskEntity.Status.OPEN, RemediationTaskEntity.Status.IN_PROGRESS)
                );
            if (existingActive.isPresent()) {
                existingTaskIds.add(existingActive.get().getTaskId());
                skippedCount++;
                continue;
            }

            if (!request.isForceReopenClosed()) {
                Optional<RemediationTaskEntity> closedExisting = remediationTaskRepository
                    .findFirstByTenantIdAndFindingFingerprintAndStatusIn(
                        tenantId,
                        fingerprint,
                        List.of(RemediationTaskEntity.Status.CLOSED, RemediationTaskEntity.Status.WAIVED)
                    );
                if (closedExisting.isPresent()) {
                    existingTaskIds.add(closedExisting.get().getTaskId());
                    skippedCount++;
                    continue;
                }
            }

            RemediationTaskEntity task = new RemediationTaskEntity();
            task.setTenantId(tenantId);
            task.setSourceId(run.getSourceId());
            task.setRunId(runId);
            task.setFindingPk(finding.getFindingId());
            task.setFindingFingerprint(fingerprint);
            task.setTitle(resolveTitle(kind));
            RemediationTaskEntity.Severity severity = resolveSeverity(kind);
            task.setSeverity(severity);
            task.setDueDate(resolveDueDate(severity));
            task.setStatus(RemediationTaskEntity.Status.OPEN);
            task.setOwnerUserId(request.getDefaultOwnerUserId());
            task.setOwnerEmail(request.getDefaultOwnerEmail());

            task = remediationTaskRepository.save(task);

            RemediationTaskEventEntity event = new RemediationTaskEventEntity();
            event.setTenantId(tenantId);
            event.setTaskId(task.getTaskId());
            event.setEventType("TASK_CREATED");
            remediationTaskEventRepository.save(event);

            createdCount++;
            createdTaskIds.add(task.getTaskId());
            createdFingerprints.add(fingerprint);
            kindCounts.merge(kind, 1, Integer::sum);
        }

        TaskGenerationResponse response = new TaskGenerationResponse();
        response.setRunId(runId);
        response.setCreatedCount(createdCount);
        response.setSkippedCount(skippedCount);
        response.setCreatedTaskIds(createdTaskIds);
        response.setExistingTaskIds(existingTaskIds);

        Map<String, Object> payload = new HashMap<>();
        payload.put("runId", runId);
        payload.put("createdCount", createdCount);
        payload.put("skippedCount", skippedCount);
        payload.put("createdTaskIds", capList(createdTaskIds));
        payload.put("fingerprints", capList(createdFingerprints));
        payload.put("kindCounts", kindCounts);

        auditWriter.write(com.regulyn.common.audit.AuditEvent.builder()
            .tenantId(tenantId)
            .action("scanner.task_created_from_finding")
            .entityType("REMEDIATION_TASK")
            .entityId(runId.toString())
            .payloadHash("N/A")
            .build());

        EventEnvelopeV1 event = EventFactory.create(
            "scanner.task_created_from_finding",
            "scanner-service",
            "REMEDIATION_TASK",
            runId.toString(),
            payload
        );
        outboxWriter.write(event);

        return response;
    }

    private Set<String> resolveKinds(TaskGenerationRequest request) {
        if (request == null || request.getKinds() == null || request.getKinds().isEmpty()) {
            return DEFAULT_KINDS;
        }
        Set<String> kinds = new HashSet<>();
        for (String kind : request.getKinds()) {
            if (kind != null && !kind.isBlank()) {
                kinds.add(kind.trim());
            }
        }
        return kinds.isEmpty() ? DEFAULT_KINDS : kinds;
    }

    private String extractKind(ScanFinding finding) {
        String kind = getString(finding.getKeyAttributes(), "kind");
        if (kind == null) {
            kind = getString(finding.getDetails(), "websiteFindingKind");
        }
        return kind;
    }

    private boolean shouldCreateTaskForKind(String kind,
                                            ScanFinding finding,
                                            String primaryHost,
                                            Set<String> allowedTrackerDomains) {
        if ("FORM_PII_DETECTED".equals(kind)) {
            String action = getString(finding.getKeyAttributes(), "formAction");
            if (action == null) {
                action = getString(finding.getDetails(), "formAction");
            }
            return action != null && !action.isBlank();
        }

        if ("TRACKER_DETECTED".equals(kind)) {
            String trackerDomain = getString(finding.getKeyAttributes(), "trackerDomain");
            if (trackerDomain == null) {
                trackerDomain = getString(finding.getDetails(), "trackerDomain");
            }
            if (trackerDomain == null || trackerDomain.isBlank()) {
                return false;
            }
            if (!isThirdParty(trackerDomain, primaryHost)) {
                return false;
            }
            return !isAllowListed(trackerDomain, allowedTrackerDomains);
        }

        return true;
    }

    private boolean isThirdParty(String domain, String primaryHost) {
        if (primaryHost == null || primaryHost.isBlank()) {
            return true;
        }
        String normalizedDomain = domain.toLowerCase(Locale.ROOT);
        String normalizedHost = primaryHost.toLowerCase(Locale.ROOT);
        return !(normalizedDomain.equals(normalizedHost) || normalizedDomain.endsWith("." + normalizedHost));
    }

    private boolean isAllowListed(String domain, Set<String> allowedDomains) {
        if (allowedDomains.isEmpty()) {
            return false;
        }
        String normalizedDomain = domain.toLowerCase(Locale.ROOT);
        for (String allowed : allowedDomains) {
            if (normalizedDomain.equals(allowed) || normalizedDomain.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> extractAllowedTrackerDomains(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Collections.emptySet();
        }
        Object value = metadata.get("allowedTrackerDomains");
        if (value == null) {
            return Collections.emptySet();
        }
        Set<String> domains = new HashSet<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null) {
                    String domain = item.toString().trim().toLowerCase(Locale.ROOT);
                    if (!domain.isBlank()) {
                        domains.add(domain);
                    }
                }
            }
        } else if (value instanceof String text) {
            for (String part : text.split(",")) {
                String domain = part.trim().toLowerCase(Locale.ROOT);
                if (!domain.isBlank()) {
                    domains.add(domain);
                }
            }
        }
        return domains;
    }

    private String resolveTitle(String kind) {
        return switch (kind) {
            case "INSECURE_FORM_ACTION_HTTP" -> "Fix insecure form submission (HTTP)";
            case "FORM_PII_DETECTED" -> "Review PII form collection and notice";
            case "TRACKER_DETECTED" -> "Review tracker usage and consent gating";
            case "UNKNOWN_THIRD_PARTY_ENDPOINT" -> "Review unknown third-party endpoints";
            default -> "Review website finding";
        };
    }

    private RemediationTaskEntity.Severity resolveSeverity(String kind) {
        return switch (kind) {
            case "INSECURE_FORM_ACTION_HTTP", "FORM_PII_DETECTED" -> RemediationTaskEntity.Severity.HIGH;
            case "TRACKER_DETECTED", "UNKNOWN_THIRD_PARTY_ENDPOINT" -> RemediationTaskEntity.Severity.MED;
            default -> RemediationTaskEntity.Severity.MED;
        };
    }

    private LocalDate resolveDueDate(RemediationTaskEntity.Severity severity) {
        int days = switch (severity) {
            case CRITICAL -> 7;
            case HIGH -> 14;
            case MED -> 21;
            case LOW -> 30;
        };
        return LocalDate.now(ZoneOffset.UTC).plusDays(days);
    }

    private String getString(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                return null;
            }
            return host.toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            return null;
        }
    }

    private <T> List<T> capList(List<T> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        if (items.size() <= EVENT_LIST_LIMIT) {
            return items;
        }
        return items.subList(0, EVENT_LIST_LIMIT);
    }
}
