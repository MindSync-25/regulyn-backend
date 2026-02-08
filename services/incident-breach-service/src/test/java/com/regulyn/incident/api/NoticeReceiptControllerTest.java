package com.regulyn.incident.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.incident.entity.NoticeDispatchLogEntity;
import com.regulyn.incident.entity.NoticeDraftEntity;
import com.regulyn.incident.entity.NoticeTemplateEntity;
import com.regulyn.incident.entity.NoticeTemplateVersionEntity;
import com.regulyn.incident.events.AuditOutboxWriter;
import com.regulyn.incident.entity.IncidentCase;
import com.regulyn.incident.repository.NoticeDispatchLogRepository;
import com.regulyn.incident.repository.IncidentCaseRepository;
import com.regulyn.incident.repository.NoticeDraftRepository;
import com.regulyn.incident.repository.NoticeTemplateRepository;
import com.regulyn.incident.repository.NoticeTemplateVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc(addFilters = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NoticeReceiptControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("incident_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (!postgres.isRunning()) {
            postgres.start();
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.schemas", () -> "incident,outbox");
        registry.add("incident.notifications.receiptWebhookSecret", () -> "test-secret");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NoticeDispatchLogRepository dispatchLogRepository;

    @Autowired
    private IncidentCaseRepository incidentCaseRepository;

    @Autowired
    private NoticeDraftRepository noticeDraftRepository;

    @Autowired
    private NoticeTemplateRepository noticeTemplateRepository;

    @Autowired
    private NoticeTemplateVersionRepository noticeTemplateVersionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuditOutboxWriter auditOutboxWriter;

    @Test
    void receiptUpdatesStatusAndIsIdempotent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();

        NoticeTemplateEntity template = new NoticeTemplateEntity();
        template.setTenantId(tenantId);
        template.setTemplateType("IMPACTED_USER_NOTICE");
        template.setName("Receipt Template");
        template.setDescription("Test");
        template.setCreatedBy("tester");
        template = noticeTemplateRepository.save(template);

        NoticeTemplateVersionEntity version = new NoticeTemplateVersionEntity();
        version.setTenantId(tenantId);
        version.setTemplateId(template.getId());
        version.setVersion(1);
        version.setLanguage("en");
        version.setContent("body");
        version.setContentSha256("hash");
        version.setVariablesSchema("[]");
        version.setCreatedBy("tester");
        version = noticeTemplateVersionRepository.save(version);

        IncidentCase incident = new IncidentCase();
        incident.setTenantId(tenantId);
        incident.setIncidentType("DATA_BREACH");
        incident.setSeverity("HIGH");
        incident.setStatus("OPENED");
        incident.setSummary("Receipt test");
        incident = incidentCaseRepository.save(incident);

        NoticeDraftEntity draft = new NoticeDraftEntity();
        draft.setId(draftId);
        draft.setTenantId(tenantId);
        draft.setIncidentId(incident.getId());
        draft.setNoticeType("IMPACTED_USER_NOTICE");
        draft.setTemplateVersionId(version.getId());
        draft.setLanguage("en");
        draft.setRenderedContent("body");
        draft.setRenderedSha256("hash");
        draft.setStatus("APPROVED");
        draft.setCreatedBy("tester");
        draft.setCreatedAt(Instant.now());
        draft.setUpdatedAt(Instant.now());
        noticeDraftRepository.save(draft);

        NoticeDispatchLogEntity log = new NoticeDispatchLogEntity();
        log.setTenantId(tenantId);
        log.setDraftId(draftId);
        log.setRecipientType("IMPACTED_USER");
        log.setRecipientIdentifier("user@example.com");
        log.setChannel("EMAIL");
        log.setStatus("SENT");
        log.setDispatchPayloadSha256("payload-hash");
        log.setQueuedAt(Instant.now());
        log.setLastStatusAt(Instant.now());
        log.setNotificationRequestId("req-123");

        log = dispatchLogRepository.save(log);

        String payload = objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
            put("tenantId", tenantId);
            put("notificationRequestId", "req-123");
            put("status", "DELIVERED");
            put("occurredAt", Instant.parse("2026-02-07T10:00:00Z"));
        }});

        mockMvc.perform(post("/incidents/notifications/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Webhook-Secret", "test-secret")
                .content(payload))
            .andExpect(status().isOk());

        NoticeDispatchLogEntity updated = dispatchLogRepository.findById(log.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("DELIVERED");
        assertThat(updated.getDeliveredAt()).isNotNull();

        mockMvc.perform(post("/incidents/notifications/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Webhook-Secret", "test-secret")
                .content(payload))
            .andExpect(status().isOk());

        verify(auditOutboxWriter, times(1)).publish(eq("NOTICE_DISPATCH_DELIVERY_UPDATED"), any(), any(), any(), any(), any());
    }

    @Test
    void receiptRejectsInvalidSecret() throws Exception {
        UUID tenantId = UUID.randomUUID();

        String payload = objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
            put("tenantId", tenantId);
            put("notificationRequestId", "req-123");
            put("status", "DELIVERED");
            put("occurredAt", Instant.parse("2026-02-07T10:00:00Z"));
        }});

        mockMvc.perform(post("/incidents/notifications/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Webhook-Secret", "wrong-secret")
                .content(payload))
            .andExpect(status().isUnauthorized());

        verify(auditOutboxWriter, times(0)).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void receiptRejectsInvalidStatusTransition() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();

        NoticeTemplateEntity template = new NoticeTemplateEntity();
        template.setTenantId(tenantId);
        template.setTemplateType("IMPACTED_USER_NOTICE");
        template.setName("Transition Template");
        template.setDescription("Test");
        template.setCreatedBy("tester");
        template = noticeTemplateRepository.save(template);

        NoticeTemplateVersionEntity version = new NoticeTemplateVersionEntity();
        version.setTenantId(tenantId);
        version.setTemplateId(template.getId());
        version.setVersion(1);
        version.setLanguage("en");
        version.setContent("body");
        version.setContentSha256("hash");
        version.setVariablesSchema("[]");
        version.setCreatedBy("tester");
        version = noticeTemplateVersionRepository.save(version);

        IncidentCase incident = new IncidentCase();
        incident.setTenantId(tenantId);
        incident.setIncidentType("DATA_BREACH");
        incident.setSeverity("HIGH");
        incident.setStatus("OPENED");
        incident.setSummary("Transition test");
        incident = incidentCaseRepository.save(incident);

        NoticeDraftEntity draft = new NoticeDraftEntity();
        draft.setId(draftId);
        draft.setTenantId(tenantId);
        draft.setIncidentId(incident.getId());
        draft.setNoticeType("IMPACTED_USER_NOTICE");
        draft.setTemplateVersionId(version.getId());
        draft.setLanguage("en");
        draft.setRenderedContent("body");
        draft.setRenderedSha256("hash");
        draft.setStatus("APPROVED");
        draft.setCreatedBy("tester");
        draft.setCreatedAt(Instant.now());
        draft.setUpdatedAt(Instant.now());
        noticeDraftRepository.save(draft);

        NoticeDispatchLogEntity log = new NoticeDispatchLogEntity();
        log.setTenantId(tenantId);
        log.setDraftId(draftId);
        log.setRecipientType("IMPACTED_USER");
        log.setRecipientIdentifier("user@example.com");
        log.setChannel("EMAIL");
        log.setStatus("DELIVERED");
        log.setDispatchPayloadSha256("payload-hash");
        log.setQueuedAt(Instant.now());
        log.setLastStatusAt(Instant.now());
        log.setNotificationRequestId("req-456");
        dispatchLogRepository.save(log);

        String payload = objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
            put("tenantId", tenantId);
            put("notificationRequestId", "req-456");
            put("status", "SENT");
            put("occurredAt", Instant.parse("2026-02-07T10:00:00Z"));
        }});

        mockMvc.perform(post("/incidents/notifications/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Webhook-Secret", "test-secret")
                .content(payload))
            .andExpect(status().isConflict());
    }
}
