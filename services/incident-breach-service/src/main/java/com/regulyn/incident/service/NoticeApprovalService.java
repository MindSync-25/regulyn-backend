package com.regulyn.incident.service;

import com.regulyn.incident.entity.NoticeApprovalEntity;
import com.regulyn.incident.entity.NoticeDraftEntity;
import com.regulyn.incident.events.AuditOutboxWriter;
import com.regulyn.incident.exception.ApprovalStateConflictException;
import com.regulyn.incident.exception.DraftNotFoundException;
import com.regulyn.incident.exception.MakerCheckerViolationException;
import com.regulyn.incident.repository.NoticeApprovalRepository;
import com.regulyn.incident.repository.NoticeDraftRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class NoticeApprovalService {

    private final NoticeApprovalRepository approvalRepository;
    private final NoticeDraftRepository draftRepository;
    private final AuditOutboxWriter auditOutboxWriter;
    private final boolean makerCheckerEnabled;

    public NoticeApprovalService(NoticeApprovalRepository approvalRepository,
                                 NoticeDraftRepository draftRepository,
                                 AuditOutboxWriter auditOutboxWriter,
                                 @Value("${incident.round2.notices.makerCheckerEnabled:true}") boolean makerCheckerEnabled) {
        this.approvalRepository = approvalRepository;
        this.draftRepository = draftRepository;
        this.auditOutboxWriter = auditOutboxWriter;
        this.makerCheckerEnabled = makerCheckerEnabled;
    }

    @Transactional
    public NoticeApprovalEntity requestApprovalIfRequired(UUID tenantId, UUID draftId, UUID actorId, String comment) {
        if (!makerCheckerEnabled) {
            return null;
        }

        NoticeDraftEntity draft = draftRepository.findByIdAndTenantId(draftId, tenantId)
                .orElseThrow(() -> new DraftNotFoundException("Draft not found"));

        UUID approvalRequestId = deterministicApprovalRequestId(draftId);
        Optional<NoticeApprovalEntity> existing = approvalRepository.findByApprovalRequestId(approvalRequestId);
        if (existing.isPresent()) {
            return existing.get();
        }

        NoticeApprovalEntity approval = new NoticeApprovalEntity();
        approval.setTenantId(tenantId);
        approval.setApprovalRequestId(approvalRequestId);
        approval.setDraftId(draftId);
        approval.setStatus("REQUESTED");
        approval.setRequestedAt(Instant.now());
        approval.setRequestedBy(actorId != null ? actorId.toString() : "system");
        approval.setRequestedComment(comment);

        boolean created = false;
        try {
            approval = approvalRepository.save(approval);
            created = true;
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueViolation(ex)) {
                approval = approvalRepository.findByApprovalRequestId(approvalRequestId).orElseThrow(() -> ex);
            } else {
                throw ex;
            }
        }

        if ("DRAFT".equals(draft.getStatus()) || "APPROVAL_PENDING".equals(draft.getStatus())) {
            draft.setStatus("APPROVAL_PENDING");
            draftRepository.save(draft);
        }

        if (created) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("tenant_id", tenantId.toString());
            payload.put("draft_id", draft.getId().toString());
            payload.put("approval_request_id", approvalRequestId.toString());
            payload.put("incident_id", draft.getIncidentId().toString());
            payload.put("notice_type", draft.getNoticeType());
            payload.put("rendered_sha256", draft.getRenderedSha256());
            payload.put("requested_by", approval.getRequestedBy());
            payload.put("requested_at", approval.getRequestedAt().toString());

            auditOutboxWriter.publish(
                    "NOTICE_APPROVAL_REQUESTED",
                    "notice_approval",
                    approval.getId() != null ? approval.getId().toString() : approvalRequestId.toString(),
                    tenantId,
                    actorId,
                    payload
            );
        }

        return approval;
    }

    @Transactional
    public NoticeApprovalEntity approveDraft(UUID tenantId, UUID draftId, UUID approverActor, String comment) {
        NoticeDraftEntity draft = draftRepository.findByIdAndTenantId(draftId, tenantId)
                .orElseThrow(() -> new DraftNotFoundException("Draft not found"));

        if (makerCheckerEnabled && approverActor != null) {
            if (approverActor.toString().equals(draft.getCreatedBy())) {
                throw new MakerCheckerViolationException("Maker-checker violation: approver cannot be creator");
            }
        }

        UUID approvalRequestId = deterministicApprovalRequestId(draftId);
        NoticeApprovalEntity approval = approvalRepository.findByApprovalRequestId(approvalRequestId)
                .orElseGet(() -> createApprovalForApproval(draft, tenantId, approverActor));

        if ("APPROVED".equals(approval.getStatus()) || "REJECTED".equals(approval.getStatus())) {
            return approval;
        }

        approval.setStatus("APPROVED");
        approval.setDecidedAt(Instant.now());
        approval.setDecidedBy(approverActor != null ? approverActor.toString() : "system");
        approval.setDecidedComment(comment);
        approval = approvalRepository.save(approval);

        draft.setStatus("APPROVED");
        draftRepository.save(draft);

        Map<String, Object> payload = new HashMap<>();
        payload.put("tenant_id", tenantId.toString());
        payload.put("draft_id", draft.getId().toString());
        payload.put("approval_request_id", approvalRequestId.toString());
        payload.put("incident_id", draft.getIncidentId().toString());
        payload.put("notice_type", draft.getNoticeType());
        payload.put("rendered_sha256", draft.getRenderedSha256());
        payload.put("decided_by", approval.getDecidedBy());
        payload.put("decided_at", approval.getDecidedAt().toString());
        payload.put("decided_comment_present", comment != null && !comment.isBlank());

        auditOutboxWriter.publish(
                "NOTICE_APPROVED",
                "notice_approval",
                approval.getId() != null ? approval.getId().toString() : approvalRequestId.toString(),
                tenantId,
                approverActor,
                payload
        );

        return approval;
    }

    @Transactional
    public NoticeApprovalEntity rejectDraft(UUID tenantId, UUID draftId, UUID approverActor, String comment) {
        NoticeDraftEntity draft = draftRepository.findByIdAndTenantId(draftId, tenantId)
                .orElseThrow(() -> new DraftNotFoundException("Draft not found"));

        if (makerCheckerEnabled && approverActor != null) {
            if (approverActor.toString().equals(draft.getCreatedBy())) {
                throw new MakerCheckerViolationException("Maker-checker violation: approver cannot be creator");
            }
        }

        UUID approvalRequestId = deterministicApprovalRequestId(draftId);
        NoticeApprovalEntity approval = approvalRepository.findByApprovalRequestId(approvalRequestId)
                .orElseGet(() -> createApprovalForApproval(draft, tenantId, approverActor));

        if ("APPROVED".equals(approval.getStatus()) || "REJECTED".equals(approval.getStatus())) {
            return approval;
        }

        approval.setStatus("REJECTED");
        approval.setDecidedAt(Instant.now());
        approval.setDecidedBy(approverActor != null ? approverActor.toString() : "system");
        approval.setDecidedComment(comment);
        approval = approvalRepository.save(approval);

        draft.setStatus("REJECTED");
        draftRepository.save(draft);

        Map<String, Object> payload = new HashMap<>();
        payload.put("tenant_id", tenantId.toString());
        payload.put("draft_id", draft.getId().toString());
        payload.put("approval_request_id", approvalRequestId.toString());
        payload.put("incident_id", draft.getIncidentId().toString());
        payload.put("notice_type", draft.getNoticeType());
        payload.put("rendered_sha256", draft.getRenderedSha256());
        payload.put("decided_by", approval.getDecidedBy());
        payload.put("decided_at", approval.getDecidedAt().toString());
        payload.put("decided_comment_present", comment != null && !comment.isBlank());

        auditOutboxWriter.publish(
                "NOTICE_REJECTED",
                "notice_approval",
                approval.getId() != null ? approval.getId().toString() : approvalRequestId.toString(),
                tenantId,
                approverActor,
                payload
        );

        return approval;
    }

    private NoticeApprovalEntity createApprovalForApproval(NoticeDraftEntity draft, UUID tenantId, UUID actorId) {
        if (!makerCheckerEnabled) {
            NoticeApprovalEntity approval = new NoticeApprovalEntity();
            approval.setTenantId(tenantId);
            approval.setApprovalRequestId(deterministicApprovalRequestId(draft.getId()));
            approval.setDraftId(draft.getId());
            approval.setStatus("REQUESTED");
            approval.setRequestedAt(Instant.now());
            approval.setRequestedBy(actorId != null ? actorId.toString() : "system");
            return approvalRepository.save(approval);
        }

        NoticeApprovalEntity approval = requestApprovalIfRequired(tenantId, draft.getId(), actorId, "auto-request");
        if (approval == null) {
            throw new ApprovalStateConflictException("Approval request missing");
        }
        return approval;
    }

    private UUID deterministicApprovalRequestId(UUID draftId) {
        String source = "APPROVAL_REQUEST:" + draftId;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
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
