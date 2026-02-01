package com.regulyn.incident.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.incident.client.EvidenceServiceClient;
import com.regulyn.incident.client.NotificationServiceClient;
import com.regulyn.incident.dto.*;
import com.regulyn.incident.entity.*;
import com.regulyn.incident.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
public class IncidentService {
    
    private static final Logger logger = LoggerFactory.getLogger(IncidentService.class);
    
    private static final Map<String, Set<String>> STATE_TRANSITIONS = Map.of(
        "OPENED", Set.of("TRIAGED"),
        "TRIAGED", Set.of("INVESTIGATING", "CONTAINED"),
        "INVESTIGATING", Set.of("NOTIFIED", "CONTAINED"),
        "NOTIFIED", Set.of("CONTAINED"),
        "CONTAINED", Set.of("CLOSED"),
        "CLOSED", Set.of()
    );
    
    private final IncidentCaseRepository incidentRepository;
    private final IncidentTaskRepository taskRepository;
    private final IncidentNotificationRepository notificationRepository;
    private final IncidentStatusHistoryRepository historyRepository;
    private final EvidenceServiceClient evidenceClient;
    private final NotificationServiceClient notificationClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    
    public IncidentService(
            IncidentCaseRepository incidentRepository,
            IncidentTaskRepository taskRepository,
            IncidentNotificationRepository notificationRepository,
            IncidentStatusHistoryRepository historyRepository,
            EvidenceServiceClient evidenceClient,
            NotificationServiceClient notificationClient,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper) {
        this.incidentRepository = incidentRepository;
        this.taskRepository = taskRepository;
        this.notificationRepository = notificationRepository;
        this.historyRepository = historyRepository;
        this.evidenceClient = evidenceClient;
        this.notificationClient = notificationClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }
    
    @Transactional
    public CreateIncidentResponse createIncident(CreateIncidentRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();
        
        IncidentCase incident = new IncidentCase();
        incident.setTenantId(tenantId);
        incident.setIncidentType("DATA_BREACH");  // Set incident type (currently defaulting to DATA_BREACH)
        incident.setSeverity(request.severity());
        incident.setStatus("OPENED");
        incident.setSummary(request.summary());
        
        try {
            incident.setMetadata(objectMapper.writeValueAsString(request.metadata()));
        } catch (JsonProcessingException e) {
            incident.setMetadata("{}");
        }
        
        incident = incidentRepository.save(incident);
        
        writeAudit(actorId, "incident.created", "IncidentCase", incident.getId(), 
            Map.of("severity", incident.getSeverity()));
        writeOutbox(tenantId, "incident.created", Map.of(
            "incidentId", incident.getId().toString(),
            "status", incident.getStatus(),
            "severity", incident.getSeverity()
        ));
        
        logger.info("Created incident: {}", incident.getId());
        
        return new CreateIncidentResponse(incident.getId(), incident.getStatus(), incident.getNotifyDueAt());
    }
    
    @Transactional
    public CreateTaskResponse createTask(UUID incidentId, CreateTaskRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();
        
        IncidentCase incident = getIncident(incidentId);
        
        IncidentTask task = new IncidentTask();
        task.setTenantId(tenantId);
        task.setIncidentId(incidentId);
        task.setTaskType(request.taskType());
        task.setStatus("OPEN");
        task.setAssignedTo(request.assignedTo());
        task.setNotes(request.notes());
        
        task = taskRepository.save(task);
        
        writeAudit(actorId, "incident.task_created", "IncidentTask", task.getTaskId(), 
            Map.of("taskType", task.getTaskType()));
        writeOutbox(tenantId, "incident.task_created", Map.of(
            "incidentId", incidentId.toString(),
            "taskId", task.getTaskId().toString(),
            "taskType", task.getTaskType()
        ));
        
        logger.info("Created task {} for incident {}", task.getTaskId(), incidentId);
        
        return new CreateTaskResponse(task.getTaskId(), task.getStatus());
    }
    
    @Transactional
    public TransitionResponse transitionStatus(UUID incidentId, TransitionRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();
        
        IncidentCase incident = getIncident(incidentId);
        
        String currentStatus = incident.getStatus();
        String newStatus = request.toStatus();
        
        validateTransition(currentStatus, newStatus);
        
        IncidentStatusHistory history = new IncidentStatusHistory();
        history.setTenantId(tenantId);
        history.setIncidentId(incidentId);
        history.setFromStatus(currentStatus);
        history.setToStatus(newStatus);
        history.setChangedBy(actorId);
        history.setReason(request.reason());
        historyRepository.save(history);
        
        incident.setStatus(newStatus);
        incidentRepository.save(incident);
        
        writeAudit(actorId, "incident.status_changed", "IncidentCase", incidentId,
            Map.of("fromStatus", currentStatus, "toStatus", newStatus));
        writeOutbox(tenantId, "incident.status_changed", Map.of(
            "incidentId", incidentId.toString(),
            "fromStatus", currentStatus,
            "toStatus", newStatus
        ));
        
        logger.info("Transitioned incident {} from {} to {}", incidentId, currentStatus, newStatus);
        
        return new TransitionResponse(incidentId, newStatus);
    }
    
    @Transactional
    public DraftNotificationResponse draftNotification(UUID incidentId, DraftNotificationRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();
        
        IncidentCase incident = getIncident(incidentId);
        
        IncidentNotification notification = new IncidentNotification();
        notification.setTenantId(tenantId);
        notification.setIncidentId(incidentId);
        notification.setChannel(request.channel());
        notification.setDraftText(request.draftText());
        notification.setStatus("DRAFT");
        
        notification = notificationRepository.save(notification);
        
        writeAudit(actorId, "incident.notice_drafted", "IncidentNotification", notification.getNotificationId(),
            Map.of("channel", notification.getChannel()));
        writeOutbox(tenantId, "incident.notice_drafted", Map.of(
            "notificationId", notification.getNotificationId().toString(),
            "channel", notification.getChannel()
        ));
        
        logger.info("Drafted notification {} for incident {}", notification.getNotificationId(), incidentId);
        
        return new DraftNotificationResponse(notification.getNotificationId(), notification.getStatus());
    }
    
    @Transactional
    public ApproveNotificationResponse approveNotification(UUID incidentId, UUID notificationId, ApproveNotificationRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();
        Set<String> roles = context.getRoles();
        String actorRole = roles != null && !roles.isEmpty() ? roles.iterator().next() : null;
        
        if (actorRole == null || !Set.of("TENANT_ADMIN", "DPO", "REVIEWER").contains(actorRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only TENANT_ADMIN, DPO, or REVIEWER can approve notifications");
        }
        
        IncidentNotification notification = notificationRepository
            .findByTenantIdAndNotificationId(tenantId, notificationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        
        if (!notification.getIncidentId().equals(incidentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notification does not belong to incident");
        }
        
        if (!"DRAFT".equals(notification.getStatus())) {
            throw new IllegalStateException("Can only approve notifications in DRAFT status");
        }
        
        notification.setStatus("APPROVED");
        notification.setApprovedBy(actorId);
        notification.setApprovedAt(Instant.now());
        
        notificationRepository.save(notification);
        
        writeAudit(actorId, "incident.notice_approved", "IncidentNotification", notificationId,
            Map.of("approvedBy", actorId.toString()));
        writeOutbox(tenantId, "incident.notice_approved", Map.of(
            "notificationId", notificationId.toString(),
            "status", "APPROVED"
        ));
        
        logger.info("Approved notification {} by {}", notificationId, actorId);
        
        return new ApproveNotificationResponse(notificationId, notification.getStatus());
    }
    
    @Transactional
    public SendNotificationResponse sendNotification(UUID incidentId, UUID notificationId) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();
        
        IncidentNotification notification = notificationRepository
            .findByTenantIdAndNotificationId(tenantId, notificationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        
        if (!notification.getIncidentId().equals(incidentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notification does not belong to incident");
        }
        
        if (!"APPROVED".equals(notification.getStatus())) {
            throw new IllegalStateException("Can only send APPROVED notifications");
        }
        
        Map<String, Object> dispatchResult = notificationClient.sendNotification(
            tenantId,
            notification.getChannel(),
            notification.getDraftText(),
            notificationId
        );
        
        notification.setStatus("SENT");
        notification.setSentAt(Instant.now());
        notification.setAttempts(notification.getAttempts() + 1);
        
        try {
            notification.setDispatchLog(objectMapper.writeValueAsString(dispatchResult));
        } catch (JsonProcessingException e) {
            notification.setDispatchLog("{}");
        }
        
        notificationRepository.save(notification);
        
        writeAudit(actorId, "incident.notice_sent", "IncidentNotification", notificationId,
            Map.of("channel", notification.getChannel()));
        writeOutbox(tenantId, "incident.notice_sent", Map.of(
            "notificationId", notificationId.toString(),
            "sentAt", notification.getSentAt().toString()
        ));
        
        logger.info("Sent notification {}", notificationId);
        
        return new SendNotificationResponse(notificationId, notification.getStatus(), notification.getSentAt());
    }
    
    @Transactional
    public CloseIncidentResponse closeIncident(UUID incidentId, CloseIncidentRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();
        
        IncidentCase incident = getIncident(incidentId);
        
        if (!"CONTAINED".equals(incident.getStatus())) {
            throw new IllegalStateException("Can only close incidents in CONTAINED status");
        }
        
        UUID evidenceId = evidenceClient.createEvidence(
            tenantId,
            "Incident closure: " + (incident.getSummary() != null ? incident.getSummary() : incidentId),
            Map.of(
                "incidentId", incidentId.toString(),
                "severity", incident.getSeverity()
            )
        );
        
        List<UUID> allEvidenceIds = new ArrayList<>();
        allEvidenceIds.add(evidenceId);
        if (request.includeEvidenceIds() != null) {
            allEvidenceIds.addAll(request.includeEvidenceIds());
        }
        
        UUID bundleId = evidenceClient.createBundle(
            tenantId,
            "INCIDENT",
            "INCIDENT",
            incidentId,
            allEvidenceIds
        );
        
        incident.setStatus("CLOSED");
        incident.setClosedAt(Instant.now());
        incident.setEvidenceBundleId(bundleId);
        incident.setClosureNotes(request.closureNotes());
        
        incidentRepository.save(incident);
        
        writeAudit(actorId, "incident.closed", "IncidentCase", incidentId,
            Map.of("bundleId", bundleId.toString()));
        writeOutbox(tenantId, "incident.closed", Map.of(
            "incidentId", incidentId.toString(),
            "status", "CLOSED",
            "bundleId", bundleId.toString()
        ));
        
        logger.info("Closed incident {} with bundle {}", incidentId, bundleId);
        
        return new CloseIncidentResponse(incidentId, incident.getStatus(), bundleId);
    }
    
    @Transactional(readOnly = true)
    public IncidentDetailsResponse getIncidentDetails(UUID incidentId) {
        IncidentCase incident = getIncident(incidentId);
        
        Map<String, Object> metadata = Map.of();
        try {
            metadata = objectMapper.readValue(incident.getMetadata(), Map.class);
        } catch (Exception e) {
            // ignore
        }
        
        return new IncidentDetailsResponse(
            incident.getId(),
            incident.getTenantId(),
            incident.getStatus(),
            incident.getSeverity(),
            incident.getOpenedAt(),
            incident.getNotifyDueAt(),
            incident.getUpdatedAt(),
            incident.getSummary(),
            incident.getApprovedBy(),
            incident.getApprovedAt(),
            incident.getClosedAt(),
            incident.getEvidenceBundleId(),
            incident.getClosureNotes(),
            incident.getNotifyOverdue(),
            metadata
        );
    }
    
    @Transactional(readOnly = true)
    public Page<IncidentDetailsResponse> listIncidents(String status, String severity, Pageable pageable) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        
        Page<IncidentCase> incidents;
        if (status != null && severity != null) {
            incidents = incidentRepository.findByTenantIdAndStatusAndSeverity(tenantId, status, severity, pageable);
        } else if (status != null) {
            incidents = incidentRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else if (severity != null) {
            incidents = incidentRepository.findByTenantIdAndSeverity(tenantId, severity, pageable);
        } else {
            incidents = incidentRepository.findByTenantId(tenantId, pageable);
        }
        
        return incidents.map(incident -> {
            Map<String, Object> metadata = Map.of();
            try {
                metadata = objectMapper.readValue(incident.getMetadata(), Map.class);
            } catch (Exception e) {
                // ignore
            }
            
            return new IncidentDetailsResponse(
                incident.getId(),
                incident.getTenantId(),
                incident.getStatus(),
                incident.getSeverity(),
                incident.getOpenedAt(),
                incident.getNotifyDueAt(),
                incident.getUpdatedAt(),
                incident.getSummary(),
                incident.getApprovedBy(),
                incident.getApprovedAt(),
                incident.getClosedAt(),
                incident.getEvidenceBundleId(),
                incident.getClosureNotes(),
                incident.getNotifyOverdue(),
                metadata
            );
        });
    }
    
    private IncidentCase getIncident(UUID incidentId) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        return incidentRepository.findByIdAndTenantId(incidentId, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
    }
    
    private void validateTransition(String from, String to) {
        Set<String> allowed = STATE_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new IllegalStateException("Invalid transition from " + from + " to " + to);
        }
    }
    
    private void writeAudit(UUID actorId, String action, String entityType, UUID entityId, Map<String, ?> details) {
        try {
            String hash = hashPayload(details.toString());
            TenantContext context = TenantContextHolder.getContext();
            
            AuditEvent event = AuditEvent.builder()
                .tenantId(context.getTenantId())
                .actorId(actorId)
                .actorType(AuditEvent.ActorType.USER)
                .service("incident-breach-service")
                .action(action)
                .entityType(entityType)
                .entityId(entityId.toString())
                .payloadHash(hash)
                .build();
            
            auditWriter.write(event);
        } catch (Exception e) {
            logger.error("Failed to write audit", e);
        }
    }
    
    private void writeOutbox(UUID tenantId, String eventType, Map<String, ?> payload) {
        try {
            Map<String, Object> safePayload = new HashMap<>();
            payload.forEach((k, v) -> safePayload.put(k, v != null ? v.toString() : ""));
            
            EventEnvelopeV1 event = EventFactory.create(
                eventType,
                "incident-breach-service",
                "incident_case",
                safePayload.getOrDefault("incidentId", "").toString(),
                safePayload
            );
            outboxWriter.write(event);
        } catch (Exception e) {
            logger.error("Failed to write outbox", e);
        }
    }
    
    private String hashPayload(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
