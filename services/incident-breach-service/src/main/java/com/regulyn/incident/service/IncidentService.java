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
import com.regulyn.incident.config.IncidentCloseProperties;
import com.regulyn.incident.dto.*;
import com.regulyn.incident.entity.*;
import com.regulyn.incident.exception.ApprovalRequiredException;
import com.regulyn.incident.helper.Round2DraftLocator;
import com.regulyn.incident.repository.*;
import com.regulyn.incident.util.HashingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final NoticeTemplateService noticeTemplateService;
    private final NoticeDraftService noticeDraftService;
    private final NoticeApprovalService noticeApprovalService;
    private final NoticeDraftRepository noticeDraftRepository;
    private final NoticeApprovalRepository noticeApprovalRepository;
    private final NoticeDispatchLogRepository noticeDispatchLogRepository;
    private final Round2DraftLocator round2DraftLocator;
    private final NoticeDispatchService noticeDispatchService;
    private final IncidentCloseProperties closeProperties;
    private final boolean round2NoticeBridgeEnabled;
    private final boolean makerCheckerEnabled;
    
    public IncidentService(
            IncidentCaseRepository incidentRepository,
            IncidentTaskRepository taskRepository,
            IncidentNotificationRepository notificationRepository,
            IncidentStatusHistoryRepository historyRepository,
            EvidenceServiceClient evidenceClient,
            NotificationServiceClient notificationClient,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper,
            NoticeTemplateService noticeTemplateService,
            NoticeDraftService noticeDraftService,
            NoticeApprovalService noticeApprovalService,
            NoticeDraftRepository noticeDraftRepository,
            NoticeApprovalRepository noticeApprovalRepository,
            NoticeDispatchLogRepository noticeDispatchLogRepository,
            Round2DraftLocator round2DraftLocator,
            NoticeDispatchService noticeDispatchService,
            IncidentCloseProperties closeProperties,
            @Value("${incident.round2.notices.bridgeEnabled:true}") boolean round2NoticeBridgeEnabled,
            @Value("${incident.round2.notices.makerCheckerEnabled:true}") boolean makerCheckerEnabled) {
        this.incidentRepository = incidentRepository;
        this.taskRepository = taskRepository;
        this.notificationRepository = notificationRepository;
        this.historyRepository = historyRepository;
        this.evidenceClient = evidenceClient;
        this.notificationClient = notificationClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.noticeTemplateService = noticeTemplateService;
        this.noticeDraftService = noticeDraftService;
        this.noticeApprovalService = noticeApprovalService;
        this.noticeDraftRepository = noticeDraftRepository;
        this.noticeApprovalRepository = noticeApprovalRepository;
        this.noticeDispatchLogRepository = noticeDispatchLogRepository;
        this.round2DraftLocator = round2DraftLocator;
        this.noticeDispatchService = noticeDispatchService;
        this.closeProperties = closeProperties;
        this.round2NoticeBridgeEnabled = round2NoticeBridgeEnabled;
        this.makerCheckerEnabled = makerCheckerEnabled;
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

        if (request.metadata() != null && !request.metadata().isEmpty()) {
            try {
                notification.setDispatchLog(objectMapper.writeValueAsString(request.metadata()));
            } catch (JsonProcessingException e) {
                notification.setDispatchLog("{}");
            }
        }
        
        notification = notificationRepository.save(notification);

        if (round2NoticeBridgeEnabled) {
            String templateType = "IMPACTED_USER_NOTICE";
            String templateName = "ROUND1_DRAFT_" + request.channel();
            NoticeTemplateEntity template = noticeTemplateService.createTemplate(
                tenantId,
                templateType,
                templateName,
                "Round-1 draft bridge for channel " + request.channel(),
                actorId
            );
            NoticeTemplateVersionEntity version = noticeTemplateService.createTemplateVersion(
                tenantId,
                template.getId(),
                "en",
                request.draftText(),
                "[]",
                actorId
            );
                NoticeDraftEntity draft = noticeDraftService.createDraftForIncident(
                tenantId,
                incidentId,
                version.getId(),
                templateType,
                version.getLanguage(),
                Map.of(),
                actorId
            );

                if (notification.getRound2DraftId() == null) {
                notification.setRound2DraftId(draft.getId());
                notificationRepository.save(notification);
                }

                noticeApprovalService.requestApprovalIfRequired(
                    tenantId,
                    draft.getId(),
                    actorId,
                    "auto-request"
                );
        }
        
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

        if (round2NoticeBridgeEnabled) {
            UUID draftId = round2DraftLocator.findDraftIdForNotification(tenantId, notificationId);
            noticeApprovalService.approveDraft(tenantId, draftId, actorId, request != null ? request.reason() : null);
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

        if (!round2NoticeBridgeEnabled) {
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

        UUID draftId = null;
        NoticeDraftEntity draft = null;
        if (round2NoticeBridgeEnabled) {
            draftId = round2DraftLocator.findDraftIdForNotification(tenantId, notificationId);
            draft = noticeDraftRepository.findByIdAndTenantId(draftId, tenantId)
                    .orElseThrow(() -> new ApprovalRequiredException("Draft not found"));
            if (makerCheckerEnabled && !"APPROVED".equals(draft.getStatus())) {
                throw new ApprovalRequiredException("Draft approval required before sending");
            }
        }

        NoticeDispatchCommand dispatchCommand = buildDispatchCommand(notification, draft);
        noticeDispatchService.dispatchDraftToTargets(tenantId, draftId, dispatchCommand, actorId);
        int sentCount = noticeDispatchService.sendQueuedDispatchLogs(tenantId, draftId, null, dispatchCommand.subject());

        if (sentCount > 0) {
            notification.setStatus("SENT");
            notification.setSentAt(Instant.now());
            notification.setAttempts(notification.getAttempts() + 1);
        }

        try {
            Map<String, Object> dispatchLog = new HashMap<>();
            dispatchLog.put("sentCount", sentCount);
            if (draftId != null) {
                dispatchLog.put("draftId", draftId.toString());
            }
            if (dispatchCommand.recipients() != null && !dispatchCommand.recipients().isEmpty()) {
                dispatchLog.put("recipients", dispatchCommand.recipients());
            }
            if (dispatchCommand.segmentRef() != null) {
                dispatchLog.put("segmentRef", dispatchCommand.segmentRef());
            }
            if (dispatchCommand.subject() != null) {
                dispatchLog.put("subject", dispatchCommand.subject());
            }
            notification.setDispatchLog(objectMapper.writeValueAsString(dispatchLog));
        } catch (JsonProcessingException e) {
            notification.setDispatchLog("{}");
        }

        notificationRepository.save(notification);

        if (sentCount > 0) {
            writeAudit(actorId, "incident.notice_sent", "IncidentNotification", notificationId,
                Map.of("channel", notification.getChannel()));
            writeOutbox(tenantId, "incident.notice_sent", Map.of(
                "notificationId", notificationId.toString(),
                "sentAt", notification.getSentAt().toString()
            ));
            logger.info("Sent notification {}", notificationId);
        } else {
            logger.warn("Notification {} not sent (no recipients or dispatch failure)", notificationId);
        }

        return new SendNotificationResponse(notificationId, notification.getStatus(), notification.getSentAt());
    }

    private NoticeDispatchCommand buildDispatchCommand(IncidentNotification notification, NoticeDraftEntity draft) {
        String noticeType = draft != null ? draft.getNoticeType() : "IMPACTED_USER_NOTICE";
        String recipientType = "IMPACTED_USER";
        if (noticeType.contains("AUTHORITY")) {
            recipientType = "AUTHORITY";
        } else if (noticeType.contains("BOARD")) {
            recipientType = "BOARD";
        }

        Map<String, Object> metadata = Map.of();
        if (notification.getDispatchLog() != null && !notification.getDispatchLog().isBlank()) {
            try {
                metadata = objectMapper.readValue(notification.getDispatchLog(), Map.class);
            } catch (JsonProcessingException e) {
                metadata = Map.of();
            }
        }

        List<String> recipients = extractRecipients(metadata);
        String segmentRef = metadata.get("segmentRef") != null ? metadata.get("segmentRef").toString() : null;
        String subject = metadata.get("subject") != null ? metadata.get("subject").toString() : null;

        return new NoticeDispatchCommand(recipientType, recipients, segmentRef, "EMAIL", subject);
    }

    private List<String> extractRecipients(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return List.of();
        }
        Object recipientsObj = metadata.get("recipients");
        if (recipientsObj instanceof List<?> list) {
            List<String> recipients = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    recipients.add(item.toString());
                }
            }
            return recipients;
        }
        return List.of();
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

        EvidenceBundleResult bundleResult = null;
        boolean requireEvidence = closeProperties.isRequireEvidenceBundle();
        try {
            bundleResult = buildComplianceEvidenceBundle(incident, request, requireEvidence);
        } catch (EvidenceServiceClient.EvidenceServiceUnavailableException ex) {
            if (requireEvidence) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
            }
            logger.warn("Evidence bundle creation failed but requireEvidenceBundle=false: {}", ex.getMessage());
        }

        if (requireEvidence && bundleResult == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Evidence bundle required before closing incident");
        }

        incident.setStatus("CLOSED");
        incident.setClosedAt(Instant.now());
        incident.setEvidenceBundleId(bundleResult != null ? bundleResult.bundleId : null);
        incident.setClosureNotes(request.closureNotes());
        
        incidentRepository.save(incident);
        
        if (bundleResult != null) {
            writeAudit(actorId, "INCIDENT_EVIDENCE_BUNDLE_CREATED", "IncidentCase", incidentId,
                Map.of(
                    "bundleId", bundleResult.bundleId.toString(),
                    "proofHash", bundleResult.proofHash
                ));
            writeOutbox(tenantId, "INCIDENT_EVIDENCE_BUNDLE_CREATED", Map.of(
                "incidentId", incidentId.toString(),
                "bundleId", bundleResult.bundleId.toString(),
                "proofHash", bundleResult.proofHash
            ));
        }

        writeAudit(actorId, "incident.closed", "IncidentCase", incidentId,
            Map.of("bundleId", bundleResult != null ? bundleResult.bundleId.toString() : ""));
        writeOutbox(tenantId, "incident.closed", Map.of(
            "incidentId", incidentId.toString(),
            "status", "CLOSED",
            "bundleId", bundleResult != null ? bundleResult.bundleId.toString() : ""
        ));
        
        logger.info("Closed incident {} with bundle {}", incidentId,
            bundleResult != null ? bundleResult.bundleId : null);

        return new CloseIncidentResponse(incidentId, incident.getStatus(),
            bundleResult != null ? bundleResult.bundleId : null);
    }

    private EvidenceBundleResult buildComplianceEvidenceBundle(IncidentCase incident,
                                                              CloseIncidentRequest request,
                                                              boolean requireEvidence) {
        UUID tenantId = incident.getTenantId();
        UUID incidentId = incident.getId();

        List<NoticeDraftEntity> drafts = noticeDraftRepository.findByIncidentId(incidentId);
        List<IncidentStatusHistory> histories = historyRepository.findByIncidentId(incidentId);
        List<IncidentTask> tasks = taskRepository.findByTenantIdAndIncidentId(tenantId, incidentId);

        List<Map<String, Object>> draftProofs = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (NoticeDraftEntity draft : drafts) {
            Map<String, Object> draftProof = new LinkedHashMap<>();
            draftProof.put("draft_id", draft.getId().toString());
            draftProof.put("notice_type", draft.getNoticeType());
            draftProof.put("template_version_id", draft.getTemplateVersionId().toString());
            draftProof.put("rendered_sha256", draft.getRenderedSha256());
            if (draft.getContentArtifactRef() != null) {
                draftProof.put("content_artifact_ref", draft.getContentArtifactRef());
            }
            draftProof.put("status", draft.getStatus());
            draftProof.put("created_at", draft.getCreatedAt());

            if ("DRAFT".equals(draft.getStatus()) || "APPROVAL_PENDING".equals(draft.getStatus())) {
                String missingItem = "draft_pending:" + draft.getId();
                missing.add(missingItem);
                if (requireEvidence) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Draft pending approval or completion: " + draft.getId());
                }
            }

            NoticeApprovalEntity approval = noticeApprovalRepository.findByDraftId(draft.getId()).orElse(null);
            if (approval != null) {
                Map<String, Object> approvalProof = new LinkedHashMap<>();
                approvalProof.put("approval_request_id", approval.getApprovalRequestId().toString());
                approvalProof.put("status", approval.getStatus());
                approvalProof.put("requested_by", approval.getRequestedBy());
                approvalProof.put("requested_at", approval.getRequestedAt());
                approvalProof.put("decided_by", approval.getDecidedBy());
                approvalProof.put("decided_at", approval.getDecidedAt());
                approvalProof.put("decided_comment_present", approval.getDecidedComment() != null && !approval.getDecidedComment().isBlank());
                draftProof.put("approval", approvalProof);

                boolean approvedWithin72h = approval.getDecidedAt() != null
                        && incident.getNotifyDueAt() != null
                        && !approval.getDecidedAt().isAfter(incident.getNotifyDueAt());
                draftProof.put("approved_within_72h", approvedWithin72h);
            } else {
                draftProof.put("approved_within_72h", false);
            }

            List<NoticeDispatchLogEntity> logs = noticeDispatchLogRepository.findByDraftId(draft.getId());
            boolean requiresDispatch = "APPROVED".equals(draft.getStatus()) || "DISPATCHED".equals(draft.getStatus());
            if ((logs == null || logs.isEmpty()) && requiresDispatch) {
                String missingItem = "dispatch_missing:" + draft.getId();
                missing.add(missingItem);
                if (requireEvidence) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Missing dispatch logs for draft " + draft.getId());
                }
            }

            List<Map<String, Object>> dispatchProofs = new ArrayList<>();
            if (logs != null) {
                for (NoticeDispatchLogEntity log : logs) {
                    Map<String, Object> dispatchProof = new LinkedHashMap<>();
                    dispatchProof.put("dispatch_log_id", log.getId().toString());
                    dispatchProof.put("recipient_identifier", log.getRecipientIdentifier());
                    dispatchProof.put("channel", log.getChannel());
                    dispatchProof.put("dispatch_payload_sha256", log.getDispatchPayloadSha256());
                    dispatchProof.put("notification_request_id", log.getNotificationRequestId());
                    dispatchProof.put("provider_message_id", log.getProviderMessageId());
                    dispatchProof.put("status", log.getStatus());
                    dispatchProof.put("queued_at", log.getQueuedAt());
                    dispatchProof.put("sent_at", log.getSentAt());
                    dispatchProof.put("delivered_at", log.getDeliveredAt());
                    dispatchProof.put("failed_at", log.getFailedAt());
                    if (log.getReceiptRef() != null) {
                        dispatchProof.put("receipt_ref", log.getReceiptRef());
                    }

                    boolean sentWithin72h = log.getSentAt() != null
                            && incident.getNotifyDueAt() != null
                            && !log.getSentAt().isAfter(incident.getNotifyDueAt());
                    boolean deliveredWithin72h = log.getDeliveredAt() != null
                            && incident.getNotifyDueAt() != null
                            && !log.getDeliveredAt().isAfter(incident.getNotifyDueAt());
                    dispatchProof.put("sent_within_72h", sentWithin72h);
                    dispatchProof.put("delivered_within_72h", deliveredWithin72h);
                    dispatchProofs.add(dispatchProof);
                }
            }

            draftProof.put("dispatch_logs", dispatchProofs);
            draftProofs.add(draftProof);
        }

        List<Map<String, Object>> historyProofs = new ArrayList<>();
        for (IncidentStatusHistory history : histories) {
            Map<String, Object> historyProof = new LinkedHashMap<>();
            historyProof.put("from_status", history.getFromStatus());
            historyProof.put("to_status", history.getToStatus());
            historyProof.put("changed_at", history.getChangedAt());
            historyProof.put("changed_by", history.getChangedBy());
            historyProofs.add(historyProof);
        }

        List<Map<String, Object>> taskProofs = new ArrayList<>();
        for (IncidentTask task : tasks) {
            Map<String, Object> taskProof = new LinkedHashMap<>();
            taskProof.put("task_id", task.getTaskId().toString());
            taskProof.put("task_type", task.getTaskType());
            taskProof.put("status", task.getStatus());
            taskProof.put("assigned_to", task.getAssignedTo());
            taskProof.put("created_at", task.getCreatedAt());
            taskProofs.add(taskProof);
        }

        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("incident_id", incidentId.toString());
        proof.put("tenant_id", tenantId.toString());
        proof.put("opened_at", incident.getOpenedAt());
        proof.put("sla_due_at", incident.getNotifyDueAt());
        proof.put("status_history", historyProofs);
        proof.put("tasks", taskProofs);
        proof.put("drafts", draftProofs);
        proof.put("missing", missing);

        String proofJson;
        try {
            proofJson = objectMapper.writeValueAsString(proof);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to build evidence proof");
        }

        String proofHash = HashingUtil.sha256Hex(proofJson);

        Map<String, Object> evidenceMetadata = new HashMap<>();
        evidenceMetadata.put("incidentId", incidentId.toString());
        evidenceMetadata.put("proofHash", proofHash);
        evidenceMetadata.put("proof", proof);

        UUID evidenceId = evidenceClient.createEvidence(
            tenantId,
            "Incident compliance proof: " + incidentId,
            evidenceMetadata
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

        return new EvidenceBundleResult(bundleId, evidenceId, proofHash);
    }

    private static class EvidenceBundleResult {
        private final UUID bundleId;
        private final UUID evidenceId;
        private final String proofHash;

        private EvidenceBundleResult(UUID bundleId, UUID evidenceId, String proofHash) {
            this.bundleId = bundleId;
            this.evidenceId = evidenceId;
            this.proofHash = proofHash;
        }
    }

    @Transactional
    public ApproveNotificationResponse rejectNotification(UUID incidentId, UUID notificationId, ApproveNotificationRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();

        IncidentNotification notification = notificationRepository
                .findByTenantIdAndNotificationId(tenantId, notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        if (!notification.getIncidentId().equals(incidentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notification does not belong to incident");
        }

        if (round2NoticeBridgeEnabled) {
            UUID draftId = round2DraftLocator.findDraftIdForNotification(tenantId, notificationId);
            noticeApprovalService.rejectDraft(tenantId, draftId, actorId, request != null ? request.reason() : null);
        }

        notification.setStatus("REJECTED");
        notification.setApprovedBy(actorId);
        notification.setApprovedAt(Instant.now());
        notificationRepository.save(notification);

        return new ApproveNotificationResponse(notificationId, notification.getStatus());
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
