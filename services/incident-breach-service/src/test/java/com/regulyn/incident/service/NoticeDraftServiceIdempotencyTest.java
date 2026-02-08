package com.regulyn.incident.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.incident.entity.NoticeDraftEntity;
import com.regulyn.incident.entity.NoticeTemplateVersionEntity;
import com.regulyn.incident.events.AuditOutboxWriter;
import com.regulyn.incident.repository.NoticeDraftRepository;
import com.regulyn.incident.repository.NoticeTemplateVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NoticeDraftServiceIdempotencyTest {

    private NoticeDraftRepository draftRepository;
    private NoticeTemplateVersionRepository versionRepository;
    private AuditOutboxWriter auditOutboxWriter;
    private NoticeDraftService noticeDraftService;

    private UUID tenantId;
    private UUID incidentId;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        draftRepository = mock(NoticeDraftRepository.class);
        versionRepository = mock(NoticeTemplateVersionRepository.class);
        auditOutboxWriter = mock(AuditOutboxWriter.class);
        noticeDraftService = new NoticeDraftService(draftRepository, versionRepository, auditOutboxWriter, new ObjectMapper());

        tenantId = UUID.randomUUID();
        incidentId = UUID.randomUUID();
        versionId = UUID.randomUUID();
    }

    @Test
    void createDraft_returnsExistingOnUniqueViolation_withoutEmittingEvents() {
        NoticeTemplateVersionEntity version = new NoticeTemplateVersionEntity();
        version.setId(versionId);
        version.setTemplateId(UUID.randomUUID());
        version.setLanguage("en");
        version.setVersion(1);
        version.setContent("Hello");
        version.setVariablesSchema("[]");

        NoticeDraftEntity existing = new NoticeDraftEntity();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(tenantId);
        existing.setIncidentId(incidentId);
        existing.setTemplateVersionId(versionId);
        existing.setNoticeType("IMPACTED_USER_NOTICE");
        existing.setLanguage("en");
        existing.setRenderedContent("Hello");
        existing.setRenderedSha256("hash");
        existing.setStatus("DRAFT");
        existing.setCreatedBy("tester");
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(draftRepository.save(any())).thenThrow(uniqueViolation());
        when(draftRepository.findByIncidentIdAndTemplateVersionIdAndNoticeType(incidentId, versionId, "IMPACTED_USER_NOTICE"))
                .thenReturn(Optional.of(existing));

        NoticeDraftEntity result = noticeDraftService.createDraftForIncident(
                tenantId,
                incidentId,
                versionId,
                "IMPACTED_USER_NOTICE",
                "en",
                Map.of(),
                UUID.randomUUID()
        );

        assertThat(result).isSameAs(existing);
        verify(auditOutboxWriter, never()).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void createDraft_emitsEventsOnNewInsert() {
        NoticeTemplateVersionEntity version = new NoticeTemplateVersionEntity();
        version.setId(versionId);
        version.setTemplateId(UUID.randomUUID());
        version.setLanguage("en");
        version.setVersion(1);
        version.setContent("Hello");
        version.setVariablesSchema("[]");

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(draftRepository.save(any())).thenAnswer(invocation -> {
            NoticeDraftEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        NoticeDraftEntity result = noticeDraftService.createDraftForIncident(
                tenantId,
                incidentId,
                versionId,
                "IMPACTED_USER_NOTICE",
                "en",
                Map.of(),
                UUID.randomUUID()
        );

        assertThat(result.getId()).isNotNull();
        verify(auditOutboxWriter, times(1)).publish(any(), any(), any(), any(), any(), any());
    }

    private DataIntegrityViolationException uniqueViolation() {
        SQLException sqlException = new SQLException("duplicate", "23505");
        return new DataIntegrityViolationException("duplicate", sqlException);
    }
}
