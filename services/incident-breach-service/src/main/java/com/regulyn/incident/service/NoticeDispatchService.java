package com.regulyn.incident.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.regulyn.incident.client.NotificationSendResult;
import com.regulyn.incident.client.NotificationServiceClient;
import com.regulyn.incident.config.NotificationContactsProperties;
import com.regulyn.incident.dto.NoticeDispatchCommand;
import com.regulyn.incident.entity.NoticeDispatchLogEntity;
import com.regulyn.incident.entity.NoticeDraftEntity;
import com.regulyn.incident.events.AuditOutboxWriter;
import com.regulyn.incident.exception.ApprovalRequiredException;
import com.regulyn.incident.exception.DraftNotFoundException;
import com.regulyn.incident.repository.NoticeDispatchLogRepository;
import com.regulyn.incident.repository.NoticeDraftRepository;
import com.regulyn.incident.util.HashingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Service
public class NoticeDispatchService {

    private static final Logger logger = LoggerFactory.getLogger(NoticeDispatchService.class);

    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_SENT = "SENT";
    private static final String CHANNEL_EMAIL = "EMAIL";

    private final NoticeDispatchLogRepository dispatchLogRepository;
    private final NoticeDraftRepository draftRepository;
    private final NotificationServiceClient notificationClient;
    private final AuditOutboxWriter auditOutboxWriter;
    private final NotificationContactsProperties contactsProperties;
    private final ObjectMapper objectMapper;
    private final boolean makerCheckerEnabled;

    public NoticeDispatchService(NoticeDispatchLogRepository dispatchLogRepository,
                                 NoticeDraftRepository draftRepository,
                                 NotificationServiceClient notificationClient,
                                 AuditOutboxWriter auditOutboxWriter,
                                 NotificationContactsProperties contactsProperties,
                                 ObjectMapper objectMapper,
                                 @Value("${incident.round2.notices.makerCheckerEnabled:true}") boolean makerCheckerEnabled) {
        this.dispatchLogRepository = dispatchLogRepository;
        this.draftRepository = draftRepository;
        this.notificationClient = notificationClient;
        this.auditOutboxWriter = auditOutboxWriter;
        this.contactsProperties = contactsProperties;
        this.objectMapper = objectMapper;
        this.makerCheckerEnabled = makerCheckerEnabled;
    }

    @Transactional
    public List<NoticeDispatchLogEntity> dispatchDraftToTargets(UUID tenantId,
                                                               UUID draftId,
                                                               NoticeDispatchCommand command,
                                                               UUID actorId) {
        NoticeDraftEntity draft = draftRepository.findByIdAndTenantId(draftId, tenantId)
                .orElseThrow(() -> new DraftNotFoundException("Draft not found"));

        if (makerCheckerEnabled && !"APPROVED".equals(draft.getStatus())) {
            throw new ApprovalRequiredException("Draft approval required before dispatch");
        }

        String recipientType = normalizeRecipientType(command.recipientType());
        String channel = command.channel() != null ? command.channel() : CHANNEL_EMAIL;
        String subject = resolveSubject(command.subject(), draft);

        List<String> recipients = resolveRecipients(recipientType, command);
        if (recipients.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipients required for dispatch");
        }

        List<NoticeDispatchLogEntity> createdLogs = new ArrayList<>();
        Set<String> uniqueRecipients = new LinkedHashSet<>(recipients);

        for (String recipient : uniqueRecipients) {
            String payloadJson = buildDispatchPayloadJson(tenantId, recipient, subject, draft);
            String payloadHash = HashingUtil.sha256Hex(payloadJson);

            NoticeDispatchLogEntity log = new NoticeDispatchLogEntity();
            log.setTenantId(tenantId);
            log.setDraftId(draftId);
            log.setRecipientType(recipientType);
            log.setRecipientIdentifier(recipient);
            log.setChannel(channel);
            log.setStatus(STATUS_QUEUED);
            log.setDispatchPayloadSha256(payloadHash);
            log.setQueuedAt(Instant.now());
            log.setLastStatusAt(Instant.now());

            boolean created = false;
            try {
                log = dispatchLogRepository.save(log);
                created = true;
            } catch (DataIntegrityViolationException ex) {
                if (isUniqueViolation(ex)) {
                    log = dispatchLogRepository
                            .findByDraftIdAndRecipientIdentifierAndChannel(draftId, recipient, channel)
                            .orElseThrow(() -> ex);
                } else {
                    throw ex;
                }
            }

            if (created) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("tenant_id", tenantId.toString());
                payload.put("draft_id", draft.getId().toString());
                payload.put("dispatch_log_id", log.getId().toString());
                payload.put("incident_id", draft.getIncidentId().toString());
                payload.put("notice_type", draft.getNoticeType());
                payload.put("recipient_identifier", recipient);
                payload.put("channel", channel);
                payload.put("dispatch_payload_sha256", payloadHash);
                payload.put("rendered_sha256", draft.getRenderedSha256());

                auditOutboxWriter.publish(
                        "NOTICE_DISPATCH_QUEUED",
                        "notice_dispatch_log",
                        log.getId().toString(),
                        tenantId,
                        actorId,
                        payload
                );
                createdLogs.add(log);
            }
        }

        return createdLogs;
    }

    @Transactional
    public int sendQueuedDispatchLogs(UUID tenantId, UUID draftId, List<UUID> dispatchLogIds, String subject) {
        NoticeDraftEntity draft = draftRepository.findByIdAndTenantId(draftId, tenantId)
                .orElseThrow(() -> new DraftNotFoundException("Draft not found"));

        String resolvedSubject = resolveSubject(subject, draft);
        List<NoticeDispatchLogEntity> logs;
        if (dispatchLogIds != null && !dispatchLogIds.isEmpty()) {
            logs = dispatchLogRepository.findByTenantIdAndIdIn(tenantId, dispatchLogIds);
        } else {
            logs = dispatchLogRepository.findByTenantIdAndDraftIdAndStatus(tenantId, draftId, STATUS_QUEUED);
        }

        int sentCount = 0;
        for (NoticeDispatchLogEntity log : logs) {
            if (!STATUS_QUEUED.equals(log.getStatus())) {
                continue;
            }
            try {
                NotificationSendResult result = notificationClient.sendEmail(
                        tenantId,
                        log.getRecipientIdentifier(),
                        resolvedSubject,
                        draft.getRenderedContent(),
                        draft.getId(),
                        log.getId()
                );

                if (result.status() != null && STATUS_SENT.equals(result.status())) {
                    Instant now = Instant.now();
                    int rowsUpdated = dispatchLogRepository.updateStatusIfMatches(
                            tenantId,
                            log.getId(),
                            STATUS_QUEUED,
                            STATUS_SENT,
                            result.notificationRequestId(),
                            result.providerMessageId(),
                            now,
                            now
                    );

                    if (rowsUpdated == 1) {
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("tenant_id", tenantId.toString());
                        payload.put("draft_id", draft.getId().toString());
                        payload.put("dispatch_log_id", log.getId().toString());
                        payload.put("incident_id", draft.getIncidentId().toString());
                        payload.put("notice_type", draft.getNoticeType());
                        payload.put("recipient_identifier", log.getRecipientIdentifier());
                        payload.put("channel", log.getChannel());
                        payload.put("dispatch_payload_sha256", log.getDispatchPayloadSha256());
                        payload.put("rendered_sha256", draft.getRenderedSha256());
                        payload.put("notification_request_id", result.notificationRequestId());
                        payload.put("provider_message_id", result.providerMessageId());

                        auditOutboxWriter.publish(
                                "NOTICE_DISPATCH_SENT",
                                "notice_dispatch_log",
                                log.getId().toString(),
                                tenantId,
                                null,
                                payload
                        );
                        sentCount++;
                    } else {
                        logger.warn("Dispatch log {} already moved from QUEUED; skipping SENT emit", log.getId());
                    }
                } else {
                    logger.warn("Dispatch log {} not marked SENT due to status {}", log.getId(), result.status());
                }
            } catch (RuntimeException ex) {
                logger.warn("Dispatch send failed for log {}: {}", log.getId(), ex.getMessage());
                log.setFailedAt(Instant.now());
                log.setLastStatusAt(Instant.now());
                dispatchLogRepository.save(log);
            }
        }

        return sentCount;
    }

    private List<String> resolveRecipients(String recipientType, NoticeDispatchCommand command) {
        if ("AUTHORITY".equals(recipientType)) {
            if (contactsProperties.getAuthorityContacts() == null || contactsProperties.getAuthorityContacts().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authority contacts not configured");
            }
            return contactsProperties.getAuthorityContacts();
        }
        if ("BOARD".equals(recipientType)) {
            if (contactsProperties.getBoardContacts() == null || contactsProperties.getBoardContacts().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Board contacts not configured");
            }
            return contactsProperties.getBoardContacts();
        }
        if ("IMPACTED_USER".equals(recipientType)) {
            if (command.recipients() != null && !command.recipients().isEmpty()) {
                return command.recipients();
            }
            if (command.segmentRef() != null && !command.segmentRef().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Segment dispatch not supported without recipients");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipients required for impacted user dispatch");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported recipient type: " + recipientType);
    }

    private String resolveSubject(String subject, NoticeDraftEntity draft) {
        if (subject != null && !subject.isBlank()) {
            return subject;
        }
        return "Incident Notice " + draft.getIncidentId();
    }

    private String normalizeRecipientType(String recipientType) {
        if (recipientType == null || recipientType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recipientType is required");
        }
        return recipientType.trim().toUpperCase(Locale.ROOT);
    }

    private String buildDispatchPayloadJson(UUID tenantId, String recipient, String subject, NoticeDraftEntity draft) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channel", CHANNEL_EMAIL);
        payload.put("tenant_id", tenantId.toString());
        payload.put("to", recipient);
        payload.put("subject", subject);
        payload.put("body", draft.getRenderedContent());
        payload.put("draft_id", draft.getId().toString());
        payload.put("incident_id", draft.getIncidentId().toString());

        ObjectMapper sortedMapper = objectMapper.copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        try {
            return sortedMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize dispatch payload", e);
        }
    }

    private boolean isUniqueViolation(DataIntegrityViolationException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return "23505".equals(sqlException.getSQLState());
            }
            current = current.getCause();
        }
        return false;
    }
}
