package com.regulyn.incident.service;

import com.regulyn.incident.entity.NoticeApprovalEntity;
import com.regulyn.incident.entity.NoticeDraftEntity;
import com.regulyn.incident.events.AuditOutboxWriter;
import com.regulyn.incident.repository.NoticeApprovalRepository;
import com.regulyn.incident.repository.NoticeDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NoticeApprovalServiceRequestIdempotencyTest {

    private NoticeApprovalRepository approvalRepository;
    private NoticeDraftRepository draftRepository;
    private AuditOutboxWriter auditOutboxWriter;
    private NoticeApprovalService approvalService;

    private UUID tenantId;
    private UUID draftId;

    @BeforeEach
    void setUp() {
        approvalRepository = mock(NoticeApprovalRepository.class);
        draftRepository = mock(NoticeDraftRepository.class);
        auditOutboxWriter = mock(AuditOutboxWriter.class);
        approvalService = new NoticeApprovalService(approvalRepository, draftRepository, auditOutboxWriter, true);

        tenantId = UUID.randomUUID();
        draftId = UUID.randomUUID();
    }

    @Test
    void requestApproval_isIdempotent_andEmitsOnce() {
        NoticeDraftEntity draft = new NoticeDraftEntity();
        draft.setId(draftId);
        draft.setTenantId(tenantId);
        draft.setIncidentId(UUID.randomUUID());
        draft.setNoticeType("IMPACTED_USER_NOTICE");
        draft.setRenderedSha256("hash");
        draft.setStatus("DRAFT");

        NoticeApprovalEntity approval = new NoticeApprovalEntity();
        approval.setId(UUID.randomUUID());
        approval.setTenantId(tenantId);
        approval.setDraftId(draftId);
        approval.setStatus("REQUESTED");
        approval.setRequestedAt(Instant.now());
        approval.setRequestedBy("actor");

        when(draftRepository.findByIdAndTenantId(draftId, tenantId)).thenReturn(Optional.of(draft));
        when(approvalRepository.findByApprovalRequestId(any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(approval));
        when(approvalRepository.save(any())).thenReturn(approval);

        NoticeApprovalEntity first = approvalService.requestApprovalIfRequired(tenantId, draftId, UUID.randomUUID(), "auto");
        NoticeApprovalEntity second = approvalService.requestApprovalIfRequired(tenantId, draftId, UUID.randomUUID(), "auto");

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        verify(approvalRepository, times(1)).save(any());
        verify(auditOutboxWriter, times(1)).publish(any(), any(), any(), any(), any(), any());
    }
}
