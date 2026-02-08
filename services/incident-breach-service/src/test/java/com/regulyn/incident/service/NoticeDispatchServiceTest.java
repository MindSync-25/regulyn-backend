package com.regulyn.incident.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.incident.client.NotificationSendResult;
import com.regulyn.incident.client.NotificationServiceClient;
import com.regulyn.incident.config.NotificationContactsProperties;
import com.regulyn.incident.dto.NoticeDispatchCommand;
import com.regulyn.incident.entity.NoticeDispatchLogEntity;
import com.regulyn.incident.entity.NoticeDraftEntity;
import com.regulyn.incident.events.AuditOutboxWriter;
import com.regulyn.incident.repository.NoticeDispatchLogRepository;
import com.regulyn.incident.repository.NoticeDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NoticeDispatchServiceTest {

    private NoticeDispatchLogRepository dispatchLogRepository;
    private NoticeDraftRepository draftRepository;
    private NotificationServiceClient notificationClient;
    private AuditOutboxWriter auditOutboxWriter;
    private NotificationContactsProperties contactsProperties;
    private NoticeDispatchService noticeDispatchService;

    private UUID tenantId;
    private UUID draftId;

    @BeforeEach
    void setUp() {
        dispatchLogRepository = mock(NoticeDispatchLogRepository.class);
        draftRepository = mock(NoticeDraftRepository.class);
        notificationClient = mock(NotificationServiceClient.class);
        auditOutboxWriter = mock(AuditOutboxWriter.class);
        contactsProperties = new NotificationContactsProperties();
        noticeDispatchService = new NoticeDispatchService(
                dispatchLogRepository,
                draftRepository,
                notificationClient,
                auditOutboxWriter,
                contactsProperties,
                new ObjectMapper(),
                true
        );

        tenantId = UUID.randomUUID();
        draftId = UUID.randomUUID();
    }

    @Test
    void dispatch_isIdempotentOnUniqueViolation() {
        NoticeDraftEntity draft = new NoticeDraftEntity();
        draft.setId(draftId);
        draft.setTenantId(tenantId);
        draft.setIncidentId(UUID.randomUUID());
        draft.setNoticeType("IMPACTED_USER_NOTICE");
        draft.setRenderedContent("body");
        draft.setRenderedSha256("hash");
        draft.setStatus("APPROVED");

        NoticeDispatchLogEntity existing = new NoticeDispatchLogEntity();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(tenantId);
        existing.setDraftId(draftId);
        existing.setRecipientIdentifier("user@example.com");
        existing.setChannel("EMAIL");
        existing.setStatus("QUEUED");
        existing.setDispatchPayloadSha256("payload-hash");
        existing.setQueuedAt(Instant.now());
        existing.setLastStatusAt(Instant.now());

        when(draftRepository.findByIdAndTenantId(draftId, tenantId)).thenReturn(Optional.of(draft));
        when(dispatchLogRepository.save(any())).thenThrow(uniqueViolation());
        when(dispatchLogRepository.findByDraftIdAndRecipientIdentifierAndChannel(draftId, "user@example.com", "EMAIL"))
                .thenReturn(Optional.of(existing));

        NoticeDispatchCommand command = new NoticeDispatchCommand(
                "IMPACTED_USER",
                List.of("user@example.com"),
                null,
                "EMAIL",
                "Subject"
        );

        List<NoticeDispatchLogEntity> result = noticeDispatchService.dispatchDraftToTargets(tenantId, draftId, command, UUID.randomUUID());

        assertThat(result).isEmpty();
        verify(auditOutboxWriter, never()).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void dispatch_emitsQueuedEventOnNewInsert() {
        NoticeDraftEntity draft = new NoticeDraftEntity();
        draft.setId(draftId);
        draft.setTenantId(tenantId);
        draft.setIncidentId(UUID.randomUUID());
        draft.setNoticeType("IMPACTED_USER_NOTICE");
        draft.setRenderedContent("body");
        draft.setRenderedSha256("hash");
        draft.setStatus("APPROVED");

        when(draftRepository.findByIdAndTenantId(draftId, tenantId)).thenReturn(Optional.of(draft));
        when(dispatchLogRepository.save(any())).thenAnswer(invocation -> {
            NoticeDispatchLogEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        NoticeDispatchCommand command = new NoticeDispatchCommand(
                "IMPACTED_USER",
                List.of("user@example.com"),
                null,
                "EMAIL",
                "Subject"
        );

        List<NoticeDispatchLogEntity> result = noticeDispatchService.dispatchDraftToTargets(tenantId, draftId, command, UUID.randomUUID());

        assertThat(result).hasSize(1);
        verify(auditOutboxWriter, times(1)).publish(eq("NOTICE_DISPATCH_QUEUED"), any(), any(), any(), any(), any());
    }

    @Test
    void sendQueued_updatesStatusAndEmitsEvent() {
        NoticeDraftEntity draft = new NoticeDraftEntity();
        draft.setId(draftId);
        draft.setTenantId(tenantId);
        draft.setIncidentId(UUID.randomUUID());
        draft.setNoticeType("AUTHORITY_NOTICE");
        draft.setRenderedContent("body");
        draft.setRenderedSha256("hash");
        draft.setStatus("APPROVED");

        NoticeDispatchLogEntity log = new NoticeDispatchLogEntity();
        log.setId(UUID.randomUUID());
        log.setTenantId(tenantId);
        log.setDraftId(draftId);
        log.setRecipientIdentifier("authority@example.com");
        log.setChannel("EMAIL");
        log.setStatus("QUEUED");
        log.setDispatchPayloadSha256("payload-hash");
        log.setQueuedAt(Instant.now());
        log.setLastStatusAt(Instant.now());

        when(draftRepository.findByIdAndTenantId(draftId, tenantId)).thenReturn(Optional.of(draft));
        when(dispatchLogRepository.findByTenantIdAndDraftIdAndStatus(tenantId, draftId, "QUEUED"))
                .thenReturn(List.of(log));
        when(notificationClient.sendEmail(eq(tenantId), any(), any(), any(), any(), any()))
                .thenReturn(new NotificationSendResult("req-1", "msg-1", "SENT"));
        when(dispatchLogRepository.updateStatusIfMatches(eq(tenantId), eq(log.getId()), eq("QUEUED"), eq("SENT"),
            eq("req-1"), eq("msg-1"), any(), any()))
            .thenReturn(1);

        int sentCount = noticeDispatchService.sendQueuedDispatchLogs(tenantId, draftId, null, "Subject");

        assertThat(sentCount).isEqualTo(1);
        verify(dispatchLogRepository, times(1))
            .updateStatusIfMatches(eq(tenantId), eq(log.getId()), eq("QUEUED"), eq("SENT"),
                eq("req-1"), eq("msg-1"), any(), any());
        verify(auditOutboxWriter, times(1)).publish(eq("NOTICE_DISPATCH_SENT"), any(), any(), any(), any(), any());
    }

    private DataIntegrityViolationException uniqueViolation() {
        SQLException sqlException = new SQLException("duplicate", "23505");
        return new DataIntegrityViolationException("duplicate", sqlException);
    }
}
