package com.regulyn.incident.api;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.incident.entity.IncidentCase;
import com.regulyn.incident.entity.IncidentNotification;
import com.regulyn.incident.entity.NoticeDraftEntity;
import com.regulyn.incident.entity.NoticeTemplateEntity;
import com.regulyn.incident.entity.NoticeTemplateVersionEntity;
import com.regulyn.incident.repository.IncidentCaseRepository;
import com.regulyn.incident.repository.IncidentNotificationRepository;
import com.regulyn.incident.repository.NoticeDispatchLogRepository;
import com.regulyn.incident.repository.NoticeDraftRepository;
import com.regulyn.incident.repository.NoticeTemplateRepository;
import com.regulyn.incident.repository.NoticeTemplateVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc(addFilters = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SendGatingIntegrationTest {


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
        registry.add("notification.service.stub", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IncidentCaseRepository incidentCaseRepository;

    @Autowired
    private IncidentNotificationRepository notificationRepository;

    @Autowired
    private NoticeDraftRepository noticeDraftRepository;

    @Autowired
    private NoticeTemplateRepository noticeTemplateRepository;

    @Autowired
    private NoticeTemplateVersionRepository noticeTemplateVersionRepository;

    @Autowired
    private NoticeDispatchLogRepository dispatchLogRepository;

    @Test
    void sendIsBlockedWhenDraftNotApproved() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        TenantContext context = new TenantContext();
        context.setTenantId(tenantId);
        context.setUserId(actorId);
        context.setRequestId(UUID.randomUUID().toString());
        TenantContextHolder.setContext(context);

        IncidentCase incident = new IncidentCase();
        incident.setTenantId(tenantId);
        incident.setIncidentType("DATA_BREACH");
        incident.setSeverity("HIGH");
        incident.setStatus("OPENED");
        incident.setSummary("Test");
        incident = incidentCaseRepository.save(incident);

        NoticeTemplateEntity template = new NoticeTemplateEntity();
        template.setTenantId(tenantId);
        template.setTemplateType("IMPACTED_USER_NOTICE");
        template.setName("Test Template");
        template.setDescription("Test");
        template.setCreatedBy(actorId.toString());
        template = noticeTemplateRepository.save(template);

        NoticeTemplateVersionEntity version = new NoticeTemplateVersionEntity();
        version.setTenantId(tenantId);
        version.setTemplateId(template.getId());
        version.setVersion(1);
        version.setLanguage("en");
        version.setContent("body");
        version.setContentSha256("hash");
        version.setVariablesSchema("[]");
        version.setCreatedBy(actorId.toString());
        version = noticeTemplateVersionRepository.save(version);

        NoticeDraftEntity draft = new NoticeDraftEntity();
        draft.setTenantId(tenantId);
        draft.setIncidentId(incident.getId());
        draft.setNoticeType("IMPACTED_USER_NOTICE");
        draft.setTemplateVersionId(version.getId());
        draft.setLanguage("en");
        draft.setRenderedContent("body");
        draft.setRenderedSha256("hash");
        draft.setStatus("DRAFT");
        draft.setCreatedBy(actorId.toString());
        draft.setCreatedAt(Instant.now());
        draft.setUpdatedAt(Instant.now());
        draft = noticeDraftRepository.save(draft);

        IncidentNotification notification = new IncidentNotification();
        notification.setTenantId(tenantId);
        notification.setIncidentId(incident.getId());
        notification.setChannel("EMAIL");
        notification.setDraftText("draft");
        notification.setStatus("APPROVED");
        notification.setRound2DraftId(draft.getId());
        notification = notificationRepository.save(notification);

        try {
            mockMvc.perform(post("/incidents/" + incident.getId() + "/notifications/" + notification.getNotificationId() + "/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Tenant-ID", tenantId.toString())
                    .header("X-Actor-ID", actorId.toString())
                    .header("X-Actor-Role", "TENANT_ADMIN"))
                .andExpect(status().isConflict());
        } finally {
            TenantContextHolder.clear();
        }

        long logCount = dispatchLogRepository.count();
        assertThat(logCount).isEqualTo(0L);
    }
}
