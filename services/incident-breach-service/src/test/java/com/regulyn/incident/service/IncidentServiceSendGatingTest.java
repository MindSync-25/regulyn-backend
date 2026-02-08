package com.regulyn.incident.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.incident.client.EvidenceServiceClient;
import com.regulyn.incident.client.NotificationServiceClient;
import com.regulyn.incident.config.IncidentCloseProperties;
import com.regulyn.incident.entity.IncidentNotification;
import com.regulyn.incident.entity.NoticeDraftEntity;
import com.regulyn.incident.exception.ApprovalRequiredException;
import com.regulyn.incident.helper.Round2DraftLocator;
import com.regulyn.incident.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class IncidentServiceSendGatingTest {

    private IncidentService incidentService;
    private NotificationServiceClient notificationClient;
    private IncidentNotificationRepository notificationRepository;
    private NoticeDraftRepository draftRepository;
    private Round2DraftLocator round2DraftLocator;
    private NoticeDispatchService noticeDispatchService;

    private UUID tenantId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();

        TenantContext context = new TenantContext();
        context.setTenantId(tenantId);
        context.setUserId(actorId);
        TenantContextHolder.setContext(context);

        notificationClient = mock(NotificationServiceClient.class);
        notificationRepository = mock(IncidentNotificationRepository.class);
        draftRepository = mock(NoticeDraftRepository.class);
        round2DraftLocator = mock(Round2DraftLocator.class);
        noticeDispatchService = mock(NoticeDispatchService.class);

        incidentService = new IncidentService(
                mock(IncidentCaseRepository.class),
                mock(IncidentTaskRepository.class),
                notificationRepository,
                mock(IncidentStatusHistoryRepository.class),
                mock(EvidenceServiceClient.class),
                notificationClient,
                mock(AuditWriter.class),
                mock(OutboxWriter.class),
                new ObjectMapper(),
                mock(NoticeTemplateService.class),
                mock(NoticeDraftService.class),
                mock(NoticeApprovalService.class),
                draftRepository,
            mock(NoticeApprovalRepository.class),
            mock(NoticeDispatchLogRepository.class),
                round2DraftLocator,
                noticeDispatchService,
            mock(IncidentCloseProperties.class),
                true,
                true
        );
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void send_isBlockedWhenDraftNotApproved() {
        UUID incidentId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();

        IncidentNotification notification = new IncidentNotification();
        notification.setNotificationId(notificationId);
        notification.setTenantId(tenantId);
        notification.setIncidentId(incidentId);
        notification.setStatus("APPROVED");
        notification.setDraftText("text");
        notification.setChannel("EMAIL");
        notification.setCreatedAt(Instant.now());

        NoticeDraftEntity draft = new NoticeDraftEntity();
        draft.setId(draftId);
        draft.setTenantId(tenantId);
        draft.setIncidentId(incidentId);
        draft.setStatus("DRAFT");

        when(round2DraftLocator.findDraftIdForNotification(tenantId, notificationId)).thenReturn(draftId);
        when(draftRepository.findByIdAndTenantId(draftId, tenantId)).thenReturn(Optional.of(draft));
        when(notificationRepository.findByTenantIdAndNotificationId(tenantId, notificationId))
                .thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> incidentService.sendNotification(incidentId, notificationId))
                .isInstanceOf(ApprovalRequiredException.class);

        verify(noticeDispatchService, never()).dispatchDraftToTargets(any(), any(), any(), any());
        verify(noticeDispatchService, never()).sendQueuedDispatchLogs(any(), any(), any(), any());
    }
}
