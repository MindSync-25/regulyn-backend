package io.regulyn.scanner.service;

import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.regulyn.scanner.client.EvidenceClient;
import io.regulyn.scanner.dto.RemediationTaskResponse;
import io.regulyn.scanner.dto.TaskEventRequest;
import io.regulyn.scanner.dto.TaskTransitionRequest;
import io.regulyn.scanner.model.RemediationTaskEntity;
import io.regulyn.scanner.model.RemediationTaskEventEntity;
import io.regulyn.scanner.model.ScanFinding;
import io.regulyn.scanner.repository.RemediationTaskEventRepository;
import io.regulyn.scanner.repository.RemediationTaskRepository;
import io.regulyn.scanner.repository.ScanFindingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class TaskService {

    private static final Duration IDEMPOTENCY_WINDOW = Duration.ofMinutes(5);

    private final RemediationTaskRepository remediationTaskRepository;
    private final RemediationTaskEventRepository remediationTaskEventRepository;
    private final ScanFindingRepository scanFindingRepository;
    private final EvidenceClient evidenceClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper canonicalMapper;

    public TaskService(RemediationTaskRepository remediationTaskRepository,
                       RemediationTaskEventRepository remediationTaskEventRepository,
                       ScanFindingRepository scanFindingRepository,
                       EvidenceClient evidenceClient,
                       AuditWriter auditWriter,
                       OutboxWriter outboxWriter) {
        this.remediationTaskRepository = remediationTaskRepository;
        this.remediationTaskEventRepository = remediationTaskEventRepository;
        this.scanFindingRepository = scanFindingRepository;
        this.evidenceClient = evidenceClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.canonicalMapper = new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Transactional(readOnly = true)
    public Page<RemediationTaskResponse> listTasks(RemediationTaskEntity.Status status,
                                                   UUID sourceId,
                                                   UUID runId,
                                                   RemediationTaskEntity.Severity severity,
                                                   Pageable pageable) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return remediationTaskRepository.findByFilters(tenantId, status, sourceId, runId, severity, pageable)
            .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RemediationTaskResponse getTask(UUID taskId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        RemediationTaskEntity task = remediationTaskRepository.findByTenantIdAndTaskId(tenantId, taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        return toResponse(task);
    }

    @Transactional
    public RemediationTaskResponse transitionTask(UUID taskId,
                                                  TaskTransitionRequest request,
                                                  UUID actorUserId,
                                                  String idempotencyKey) {
        UUID tenantId = TenantContextHolder.getTenantId();

        RemediationTaskEntity task = remediationTaskRepository.findByTenantIdAndTaskId(tenantId, taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        RemediationTaskEntity.Status fromStatus = task.getStatus();
        RemediationTaskEntity.Status toStatus = RemediationTaskEntity.Status.valueOf(request.getToStatus().name());

        if (fromStatus == toStatus) {
            if (idempotencyKey != null && isRecentDuplicate(tenantId, taskId, toStatus, actorUserId, request.getNotes())) {
                return toResponse(task);
            }
            if ((toStatus == RemediationTaskEntity.Status.CLOSED || toStatus == RemediationTaskEntity.Status.WAIVED)
                && task.getEvidenceArtifactRef() != null) {
                return toResponse(task);
            }
            throw new IllegalStateException("Task is already in status: " + toStatus);
        }

        enforceRoleRules(toStatus);

        if (!isAllowedTransition(fromStatus, toStatus)) {
            throw new IllegalStateException("Invalid transition from " + fromStatus + " to " + toStatus);
        }

        if (request.getOwnerUserId() != null) {
            task.setOwnerUserId(request.getOwnerUserId());
        }
        if (request.getOwnerEmail() != null) {
            task.setOwnerEmail(request.getOwnerEmail());
        }

        if (toStatus == RemediationTaskEntity.Status.CLOSED) {
            if (request.getClosureNotes() == null || request.getClosureNotes().isBlank()) {
                throw new IllegalArgumentException("closureNotes is required for CLOSED");
            }
            task.setClosureNotes(request.getClosureNotes());
            task.setClosureNotesHash(sha256(request.getClosureNotes()));
            task.setClosedAt(Instant.now());
            task.setClosedByUserId(actorUserId);
        }

        if (toStatus == RemediationTaskEntity.Status.WAIVED) {
            if (request.getWaivedReason() == null || request.getWaivedReason().isBlank()) {
                throw new IllegalArgumentException("waivedReason is required for WAIVED");
            }
            task.setWaivedReason(request.getWaivedReason());
            task.setClosedAt(Instant.now());
            task.setClosedByUserId(actorUserId);
        }

        String artifactRef = null;
        if (toStatus == RemediationTaskEntity.Status.CLOSED || toStatus == RemediationTaskEntity.Status.WAIVED) {
            if (task.getEvidenceArtifactRef() == null || task.getEvidenceArtifactRef().isBlank()) {
                artifactRef = createEvidenceArtifact(task, toStatus, actorUserId, idempotencyKey);
                task.setEvidenceArtifactRef(artifactRef);
            }
        }

        task.setStatus(toStatus);
        task = remediationTaskRepository.save(task);

        RemediationTaskEventEntity event = new RemediationTaskEventEntity();
        event.setTenantId(tenantId);
        event.setTaskId(taskId);
        event.setEventType("STATUS_CHANGED");
        event.setFromStatus(fromStatus.name());
        event.setToStatus(toStatus.name());
        event.setActorUserId(actorUserId);
        event.setNotes(request.getNotes());
        remediationTaskEventRepository.save(event);

        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", taskId);
        payload.put("fromStatus", fromStatus.name());
        payload.put("toStatus", toStatus.name());
        payload.put("actorUserId", actorUserId);
        payload.put("findingFingerprint", task.getFindingFingerprint());
        payload.put("runId", task.getRunId());
        payload.put("sourceId", task.getSourceId());

        auditWriter.write(com.regulyn.common.audit.AuditEvent.builder()
            .tenantId(tenantId)
            .action("scanner.task_status_changed")
            .entityType("REMEDIATION_TASK")
            .entityId(taskId.toString())
            .payloadHash("N/A")
            .build());

        EventEnvelopeV1 outboxEvent = EventFactory.create(
            "scanner.task_status_changed",
            "scanner-service",
            "REMEDIATION_TASK",
            taskId.toString(),
            payload
        );
        outboxWriter.write(outboxEvent);

        if (artifactRef != null) {
            Map<String, Object> artifactPayload = new HashMap<>();
            artifactPayload.put("taskId", taskId);
            artifactPayload.put("runId", task.getRunId());
            artifactPayload.put("sourceId", task.getSourceId());
            artifactPayload.put("findingFingerprint", task.getFindingFingerprint());
            artifactPayload.put("artifactRef", artifactRef);
            artifactPayload.put("status", toStatus.name());
            artifactPayload.put("actorUserId", actorUserId);
            artifactPayload.put("createdAt", Instant.now().toString());

            auditWriter.write(com.regulyn.common.audit.AuditEvent.builder()
                .tenantId(tenantId)
                .action("scanner.task_evidence_artifact_stored")
                .entityType("REMEDIATION_TASK")
                .entityId(taskId.toString())
                .payloadHash("N/A")
                .build());

            EventEnvelopeV1 artifactEvent = EventFactory.create(
                "scanner.task_evidence_artifact_stored",
                "scanner-service",
                "REMEDIATION_TASK",
                taskId.toString(),
                artifactPayload
            );
            outboxWriter.write(artifactEvent);
        }

        return toResponse(task);
    }

    @Transactional
    public RemediationTaskResponse addEvent(UUID taskId, TaskEventRequest request, UUID actorUserId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        RemediationTaskEntity task = remediationTaskRepository.findByTenantIdAndTaskId(tenantId, taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        RemediationTaskEventEntity event = new RemediationTaskEventEntity();
        event.setTenantId(tenantId);
        event.setTaskId(taskId);
        event.setEventType(request.getEventType().name());
        event.setActorUserId(actorUserId);
        event.setNotes(request.getNotes());
        if (request.getAttachmentRefs() != null) {
            event.setAttachmentRefs(request.getAttachmentRefs());
        }
        remediationTaskEventRepository.save(event);

        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", taskId);
        payload.put("eventType", request.getEventType().name());

        auditWriter.write(com.regulyn.common.audit.AuditEvent.builder()
            .tenantId(tenantId)
            .action("scanner.task_event_added")
            .entityType("REMEDIATION_TASK")
            .entityId(taskId.toString())
            .payloadHash("N/A")
            .build());

        EventEnvelopeV1 outboxEvent = EventFactory.create(
            "scanner.task_event_added",
            "scanner-service",
            "REMEDIATION_TASK",
            taskId.toString(),
            payload
        );
        outboxWriter.write(outboxEvent);

        return toResponse(task);
    }

    private boolean isAllowedTransition(RemediationTaskEntity.Status from, RemediationTaskEntity.Status to) {
        return switch (from) {
            case OPEN -> to == RemediationTaskEntity.Status.IN_PROGRESS
                || to == RemediationTaskEntity.Status.CLOSED
                || to == RemediationTaskEntity.Status.WAIVED;
            case IN_PROGRESS -> to == RemediationTaskEntity.Status.CLOSED
                || to == RemediationTaskEntity.Status.WAIVED;
            case CLOSED, WAIVED -> false;
        };
    }

    private boolean isRecentDuplicate(UUID tenantId,
                                      UUID taskId,
                                      RemediationTaskEntity.Status toStatus,
                                      UUID actorUserId,
                                      String notes) {
        return remediationTaskEventRepository
            .findTopByTenantIdAndTaskIdAndEventTypeAndToStatusAndActorUserIdAndNotesOrderByCreatedAtDesc(
                tenantId,
                taskId,
                "STATUS_CHANGED",
                toStatus.name(),
                actorUserId,
                notes
            )
            .map(event -> event.getCreatedAt().isAfter(Instant.now().minus(IDEMPOTENCY_WINDOW)))
            .orElse(false);
    }

    private void enforceRoleRules(RemediationTaskEntity.Status toStatus) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null || authentication.getAuthorities().isEmpty()) {
            return;
        }
        boolean isAdmin = hasRole(authentication, "ROLE_ADMIN");
        boolean isScannerAgent = hasRole(authentication, "ROLE_SCANNER_AGENT");

        if ((toStatus == RemediationTaskEntity.Status.CLOSED || toStatus == RemediationTaskEntity.Status.WAIVED) && !isAdmin) {
            throw new IllegalStateException("Only ADMIN can close or waive tasks");
        }
        if (toStatus == RemediationTaskEntity.Status.IN_PROGRESS && !(isAdmin || isScannerAgent)) {
            throw new IllegalStateException("Only ADMIN or SCANNER_AGENT can move tasks to IN_PROGRESS");
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (role.equalsIgnoreCase(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private RemediationTaskResponse toResponse(RemediationTaskEntity task) {
        RemediationTaskResponse response = new RemediationTaskResponse();
        response.setTaskId(task.getTaskId());
        response.setSourceId(task.getSourceId());
        response.setRunId(task.getRunId());
        response.setFindingPk(task.getFindingPk());
        response.setFindingFingerprint(task.getFindingFingerprint());
        response.setTitle(task.getTitle());
        response.setSeverity(task.getSeverity().name());
        response.setOwnerUserId(task.getOwnerUserId());
        response.setOwnerEmail(task.getOwnerEmail());
        response.setDueDate(task.getDueDate());
        response.setStatus(task.getStatus().name());
        response.setClosureNotes(task.getClosureNotes());
        response.setClosureNotesHash(task.getClosureNotesHash());
        response.setWaivedReason(task.getWaivedReason());
        response.setClosedAt(task.getClosedAt());
        response.setClosedByUserId(task.getClosedByUserId());
        response.setEvidenceArtifactRef(task.getEvidenceArtifactRef());
        response.setCreatedAt(task.getCreatedAt());
        response.setUpdatedAt(task.getUpdatedAt());
        return response;
    }

    private String createEvidenceArtifact(RemediationTaskEntity task,
                                          RemediationTaskEntity.Status toStatus,
                                          UUID actorUserId,
                                          String idempotencyKey) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Tenant-ID", tenantId.toString());
        headers.put("X-User-ID", actorUserId.toString());
        if (idempotencyKey != null) {
            headers.put("X-Idempotency-Key", idempotencyKey);
        }

        Map<String, Object> findingSnapshot = buildFindingSnapshot(tenantId, task.getFindingPk(), task.getFindingFingerprint());
        String findingSnapshotHash = sha256(canonicalJson(findingSnapshot));

        Map<String, Object> taskState = new HashMap<>();
        taskState.put("taskId", task.getTaskId());
        taskState.put("status", toStatus.name());
        taskState.put("title", task.getTitle());
        taskState.put("severity", task.getSeverity().name());
        taskState.put("dueDate", task.getDueDate() != null ? task.getDueDate().toString() : null);
        taskState.put("ownerUserId", task.getOwnerUserId());
        taskState.put("ownerEmail", task.getOwnerEmail());
        taskState.put("closedAt", task.getClosedAt() != null ? task.getClosedAt().toString() : null);
        taskState.put("closureNotesHash", task.getClosureNotesHash());
        taskState.put("waivedReason", task.getWaivedReason());

        String taskStateHash = sha256(canonicalJson(taskState));

        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("taskId", task.getTaskId().toString());
        payload.put("sourceId", task.getSourceId().toString());
        payload.put("runId", task.getRunId().toString());
        payload.put("findingFingerprint", task.getFindingFingerprint());
        payload.put("findingPk", task.getFindingPk() != null ? task.getFindingPk().toString() : null);
        payload.put("actorUserId", actorUserId.toString());
        payload.put("actorEmail", null);
        payload.put("timestamp", Instant.now().toString());
        payload.put("findingSnapshotHash", findingSnapshotHash);
        payload.put("closureNotesHash", task.getClosureNotesHash());
        payload.put("taskStateHash", taskStateHash);
        payload.put("taskSnapshot", buildTaskSnapshot(task, toStatus));
        payload.put("findingSnapshot", findingSnapshot);

        String evidenceType = toStatus == RemediationTaskEntity.Status.WAIVED
            ? "TASK_WAIVED_PROOF"
            : "TASK_CLOSED_PROOF";

        UUID evidenceId = evidenceClient.createEvidence(evidenceType, payload, headers);
        return evidenceId != null ? evidenceId.toString() : null;
    }

    private Map<String, Object> buildFindingSnapshot(UUID tenantId, UUID findingPk, String findingFingerprint) {
        if (findingPk == null) {
            return Map.of("findingFingerprint", findingFingerprint);
        }
        Optional<ScanFinding> finding = scanFindingRepository.findByTenantIdAndFindingId(tenantId, findingPk);
        if (finding.isEmpty()) {
            return Map.of("findingFingerprint", findingFingerprint);
        }
        ScanFinding entity = finding.get();
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("findingId", entity.getFindingId().toString());
        snapshot.put("findingFingerprint", entity.getFindingFingerprint());
        snapshot.put("findingType", entity.getFindingType());
        snapshot.put("entityType", entity.getEntityType());
        snapshot.put("riskLevel", entity.getRiskLevel());
        snapshot.put("normalizedSubject", entity.getNormalizedSubject());
        snapshot.put("keyAttributes", entity.getKeyAttributes());
        return snapshot;
    }

    private Map<String, Object> buildTaskSnapshot(RemediationTaskEntity task, RemediationTaskEntity.Status status) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("title", task.getTitle());
        snapshot.put("severity", task.getSeverity().name());
        snapshot.put("status", status.name());
        snapshot.put("dueDate", task.getDueDate() != null ? task.getDueDate().toString() : null);
        snapshot.put("ownerUserId", task.getOwnerUserId());
        snapshot.put("ownerEmail", task.getOwnerEmail());
        snapshot.put("closureNotesExcerpt", excerpt(task.getClosureNotes()));
        snapshot.put("closureNotesHash", task.getClosureNotesHash());
        snapshot.put("waivedReason", task.getWaivedReason());
        return snapshot;
    }

    private String canonicalJson(Object payload) {
        try {
            return canonicalMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String excerpt(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed;
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
