package com.regulyn.incident.service;

import com.regulyn.incident.entity.NoticeApprovalEntity;
import com.regulyn.incident.entity.NoticeDraftEntity;
import com.regulyn.incident.events.AuditOutboxWriter;
import com.regulyn.incident.repository.NoticeApprovalRepository;
import com.regulyn.incident.repository.NoticeDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NoticeApprovalServiceApproveIdempotencyTest {

    private NoticeApprovalRepository approvalRepository;
    private NoticeDraftRepository draftRepository;
    private AuditOutboxWriter auditOutboxWriter;
    private NoticeApprovalService approvalService;

    @BeforeEach
    void setUp() {
        approvalRepository = mock(NoticeApprovalRepository.class);
        draftRepository = mock(NoticeDraftRepository.class);
        auditOutboxWriter = mock(AuditOutboxWriter.class);
        approvalService = new NoticeApprovalService(approvalRepository, draftRepository, auditOutboxWriter, true);
    }

    @Test
    void approve_isIdempotentWhenAlreadyApproved() {
        UUID tenantId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();

        NoticeDraftEntity draft = new NoticeDraftEntity();
        draft.setId(draftId);
        draft.setTenantId(tenantId);
        draft.setIncidentId(UUID.randomUUID());
        draft.setNoticeType("IMPACTED_USER_NOTICE");
        draft.setRenderedSha256("hash");
        draft.setCreatedBy("creator");

        NoticeApprovalEntity approval = new NoticeApprovalEntity();
        approval.setId(UUID.randomUUID());
        approval.setTenantId(tenantId);
        approval.setDraftId(draftId);
        approval.setStatus("APPROVED");

        when(draftRepository.findByIdAndTenantId(draftId, tenantId)).thenReturn(Optional.of(draft));
        when(approvalRepository.findByApprovalRequestId(any())).thenReturn(Optional.of(approval));

        approvalService.approveDraft(tenantId, draftId, UUID.randomUUID(), "ok");

        verify(approvalRepository, never()).save(any());
        verify(auditOutboxWriter, never()).publish(any(), any(), any(), any(), any(), any());
    }
}
