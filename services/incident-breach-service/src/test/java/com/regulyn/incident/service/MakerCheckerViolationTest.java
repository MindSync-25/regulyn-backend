package com.regulyn.incident.service;

import com.regulyn.incident.entity.NoticeDraftEntity;
import com.regulyn.incident.events.AuditOutboxWriter;
import com.regulyn.incident.exception.MakerCheckerViolationException;
import com.regulyn.incident.repository.NoticeApprovalRepository;
import com.regulyn.incident.repository.NoticeDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MakerCheckerViolationTest {

    private NoticeApprovalService approvalService;
    private NoticeDraftRepository draftRepository;

    @BeforeEach
    void setUp() {
        NoticeApprovalRepository approvalRepository = mock(NoticeApprovalRepository.class);
        draftRepository = mock(NoticeDraftRepository.class);
        AuditOutboxWriter auditOutboxWriter = mock(AuditOutboxWriter.class);
        approvalService = new NoticeApprovalService(approvalRepository, draftRepository, auditOutboxWriter, true);
    }

    @Test
    void approve_throwsWhenMakerCheckerViolated() {
        UUID tenantId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        NoticeDraftEntity draft = new NoticeDraftEntity();
        draft.setId(draftId);
        draft.setTenantId(tenantId);
        draft.setCreatedBy(actorId.toString());

        when(draftRepository.findByIdAndTenantId(draftId, tenantId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> approvalService.approveDraft(tenantId, draftId, actorId, "ok"))
                .isInstanceOf(MakerCheckerViolationException.class);
    }
}
