package com.regulyn.incident.service;

import com.regulyn.incident.dto.NoticeReceiptRequest;
import com.regulyn.incident.entity.NoticeDispatchLogEntity;
import com.regulyn.incident.events.AuditOutboxWriter;
import com.regulyn.incident.repository.NoticeDispatchLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class NoticeReceiptService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("SENT", "DELIVERED", "FAILED_TERMINAL");

    private final NoticeDispatchLogRepository dispatchLogRepository;
    private final AuditOutboxWriter auditOutboxWriter;

    public NoticeReceiptService(NoticeDispatchLogRepository dispatchLogRepository,
                                AuditOutboxWriter auditOutboxWriter) {
        this.dispatchLogRepository = dispatchLogRepository;
        this.auditOutboxWriter = auditOutboxWriter;
    }

    @Transactional
    public NoticeDispatchLogEntity ingestReceipt(NoticeReceiptRequest request) {
        UUID tenantId = request.tenantId();
        String status = normalizeStatus(request.status());
        Instant occurredAt = request.occurredAt() != null ? request.occurredAt() : Instant.now();

        NoticeDispatchLogEntity log = findDispatchLog(tenantId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dispatch log not found"));

        if (status.equals(log.getStatus())) {
            Instant lastStatusAt = log.getLastStatusAt();
            if (lastStatusAt != null && !occurredAt.isAfter(lastStatusAt)) {
                return log;
            }

            log.setLastStatusAt(occurredAt);
            if (request.receiptRef() != null && !request.receiptRef().isBlank()) {
                log.setReceiptRef(request.receiptRef());
            }
            if ("DELIVERED".equals(status)) {
                if (log.getDeliveredAt() == null || occurredAt.isAfter(log.getDeliveredAt())) {
                    log.setDeliveredAt(occurredAt);
                }
            } else if ("FAILED_TERMINAL".equals(status)) {
                if (log.getFailedAt() == null || occurredAt.isAfter(log.getFailedAt())) {
                    log.setFailedAt(occurredAt);
                }
            } else if ("SENT".equals(status)) {
                if (log.getSentAt() == null || occurredAt.isAfter(log.getSentAt())) {
                    log.setSentAt(occurredAt);
                }
            }

            dispatchLogRepository.save(log);
            return log;
        }

        if (!isValidTransition(log.getStatus(), status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid dispatch status transition");
        }

        String oldStatus = log.getStatus();
        log.setStatus(status);
        log.setLastStatusAt(occurredAt);

        if (request.receiptRef() != null && !request.receiptRef().isBlank()) {
            log.setReceiptRef(request.receiptRef());
        }

        if ("DELIVERED".equals(status)) {
            log.setDeliveredAt(occurredAt);
        } else if ("FAILED_TERMINAL".equals(status)) {
            log.setFailedAt(occurredAt);
        } else if ("SENT".equals(status)) {
            if (log.getSentAt() == null) {
                log.setSentAt(occurredAt);
            }
        }

        dispatchLogRepository.save(log);

        Map<String, Object> payload = new HashMap<>();
        payload.put("tenant_id", tenantId.toString());
        payload.put("dispatch_log_id", log.getId().toString());
        payload.put("draft_id", log.getDraftId().toString());
        payload.put("recipient_identifier", log.getRecipientIdentifier());
        payload.put("old_status", oldStatus);
        payload.put("new_status", status);
        payload.put("notification_request_id", request.notificationRequestId());
        payload.put("provider_message_id", request.providerMessageId());
        payload.put("occurred_at", occurredAt.toString());

        auditOutboxWriter.publish(
                "NOTICE_DISPATCH_DELIVERY_UPDATED",
                "notice_dispatch_log",
                log.getId().toString(),
                tenantId,
                null,
                payload
        );

        return log;
    }

    private Optional<NoticeDispatchLogEntity> findDispatchLog(UUID tenantId, NoticeReceiptRequest request) {
        if (request.notificationRequestId() != null && !request.notificationRequestId().isBlank()) {
            return dispatchLogRepository.findByTenantIdAndNotificationRequestId(tenantId, request.notificationRequestId());
        }
        if (request.providerMessageId() != null && !request.providerMessageId().isBlank()) {
            return dispatchLogRepository.findByTenantIdAndProviderMessageId(tenantId, request.providerMessageId());
        }
        return Optional.empty();
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported status: " + status);
        }
        return normalized;
    }

    private boolean isValidTransition(String currentStatus, String newStatus) {
        if (currentStatus == null) {
            return false;
        }
        return switch (currentStatus) {
            case "QUEUED" -> "SENT".equals(newStatus) || "FAILED_TERMINAL".equals(newStatus);
            case "SENT" -> "DELIVERED".equals(newStatus) || "FAILED_TERMINAL".equals(newStatus);
            case "DELIVERED", "FAILED_TERMINAL" -> false;
            default -> false;
        };
    }
}
