package com.regulyn.notification;

import com.regulyn.auth.apikey.ApiKeyValidator;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.notification.dto.SendNotificationRequest;
import com.regulyn.notification.dto.SendNotificationResponse;
import com.regulyn.notification.entity.CommunicationPreference;
import com.regulyn.notification.entity.NotificationMessage;
import com.regulyn.notification.entity.NotificationRequest;
import com.regulyn.notification.entity.NotificationTemplate;
import com.regulyn.notification.entity.NotificationTemplateLanguage;
import com.regulyn.notification.entity.NotificationTemplateVersion;
import com.regulyn.notification.provider.NotificationProvider;
import com.regulyn.notification.provider.ProviderResult;
import com.regulyn.notification.repository.CommunicationPreferenceRepository;
import com.regulyn.notification.repository.NotificationMessageRepository;
import com.regulyn.notification.repository.NotificationRequestRepository;
import com.regulyn.notification.repository.NotificationTemplateLanguageRepository;
import com.regulyn.notification.repository.NotificationTemplateRepository;
import com.regulyn.notification.repository.NotificationTemplateVersionRepository;
import com.regulyn.notification.service.NotificationRetryWorkerService;
import com.regulyn.notification.service.NotificationSendService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest
@Testcontainers
class NotificationRound2Part5IT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("notification_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.schemas", () -> "notification");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "notification");
        registry.add("audit.schema", () -> "notification");
        registry.add("evidence.service.url", () -> "http://evidence-service");
        registry.add("consent.baseUrl", () -> "http://consent-service");
        registry.add("consent.enabled", () -> "true");
        registry.add("smtp.enabled", () -> "false");
        registry.add("notification.retry.enabled", () -> "false");
    }

    @Autowired
    private NotificationSendService sendService;

    @Autowired
    private NotificationRetryWorkerService retryWorkerService;

    @Autowired
    private NotificationMessageRepository messageRepository;

    @Autowired
    private NotificationRequestRepository requestRepository;

    @Autowired
    private NotificationTemplateRepository templateRepository;

    @Autowired
    private NotificationTemplateVersionRepository versionRepository;

    @Autowired
    private NotificationTemplateLanguageRepository languageRepository;

    @Autowired
    private CommunicationPreferenceRepository preferenceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @Autowired
    @Qualifier("consentRestTemplate")
    private RestTemplate consentRestTemplate;

    private MockRestServiceServer consentServer;

    @MockBean
    private NotificationProvider notificationProvider;

    @MockBean
    private ApiKeyValidator apiKeyValidator;

    @BeforeEach
    void setUp() {
        TenantContext context = new TenantContext();
        context.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        context.setUserId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        context.setRequestId("request-consent-001");
        TenantContextHolder.setContext(context);

        when(notificationProvider.getChannel()).thenReturn("EMAIL");
        when(notificationProvider.getProviderName()).thenReturn("MOCK");
        when(apiKeyValidator.validate(anyString()))
            .thenReturn(ApiKeyValidator.ApiKeyValidationResult.valid(UUID.randomUUID(), "test"));

        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        consentServer = MockRestServiceServer.bindTo(consentRestTemplate).build();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        jdbcTemplate.update("DELETE FROM notification.outbox_events");
        jdbcTemplate.update("DELETE FROM notification.audit_events");
        jdbcTemplate.update("DELETE FROM notification.notification_dispatch_logs");
        jdbcTemplate.update("DELETE FROM notification.notification_messages");
        jdbcTemplate.update("DELETE FROM notification.notification_requests");
        preferenceRepository.deleteAll();
        templateRepository.findAll().forEach(t -> {
            t.setActiveVersionId(null);
            templateRepository.save(t);
        });
        languageRepository.deleteAll();
        versionRepository.deleteAll();
        templateRepository.deleteAll();
    }

    @Test
    void marketing_opted_out_blocks_send() {
        createAndPublishTemplate("GENERIC", "MARKETING");

        CommunicationPreference preference = new CommunicationPreference();
        preference.setTenantId("00000000-0000-0000-0000-000000000001");
        preference.setDataPrincipalId("dp-001");
        preference.setChannel("EMAIL");
        preference.setCategory("MARKETING");
        preference.setOptedOut(true);
        preference.setUpdatedBy("tester");
        preferenceRepository.save(preference);

        mockServer.expect(requestTo("http://evidence-service/evidence"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"evidenceId\":\"evidence-consent-blocked\"}", MediaType.APPLICATION_JSON));

        SendNotificationRequest request = new SendNotificationRequest(
            null,
            "GENERIC",
            "en",
            "EMAIL",
            new SendNotificationRequest.Audience("DATA_PRINCIPAL", "dp-001", null),
            Map.of("user_name", "Jane")
        );

        SendNotificationResponse response = sendService.sendNotification(request);
        assertThat(response.sentCount()).isEqualTo(0);
        assertThat(response.skippedCount()).isEqualTo(1);
        assertThat(response.dispatches().get(0).status()).isEqualTo("CONSENT_BLOCKED");

        NotificationMessage message = messageRepository.findAll().get(0);
        assertThat(message.getStatus()).isEqualTo("CONSENT_BLOCKED");

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.audit_events WHERE action = 'NOTIFICATION_CONSENT_BLOCKED'",
            Integer.class
        );
        Integer outboxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.outbox_events WHERE event_type = 'NOTIFICATION_CONSENT_BLOCKED'",
            Integer.class
        );
        assertThat(auditCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);

        verify(notificationProvider, never()).sendEmail(any());
        mockServer.verify();
    }

    @Test
    void marketing_consent_service_timeout_fail_closed() {
        createAndPublishTemplate("GENERIC", "MARKETING");

        consentServer.expect(requestTo("http://consent-service/consent/check"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withServerError());

        mockServer.expect(requestTo("http://evidence-service/evidence"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"evidenceId\":\"evidence-consent-failed\"}", MediaType.APPLICATION_JSON));

        SendNotificationRequest request = new SendNotificationRequest(
            null,
            "GENERIC",
            "en",
            "EMAIL",
            new SendNotificationRequest.Audience("DATA_PRINCIPAL", "dp-002", null),
            Map.of("user_name", "Jane")
        );

        sendService.sendNotification(request);

        NotificationMessage message = messageRepository.findAll().get(0);
        assertThat(message.getStatus()).isEqualTo("CONSENT_BLOCKED");
        assertThat(message.getLastFailureReason()).isEqualTo("CONSENT_CHECK_FAILED");

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.audit_events WHERE action = 'NOTIFICATION_CONSENT_CHECK_FAILED'",
            Integer.class
        );
        Integer outboxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.outbox_events WHERE event_type = 'NOTIFICATION_CONSENT_CHECK_FAILED'",
            Integer.class
        );
        assertThat(auditCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);

        verify(notificationProvider, never()).sendEmail(any());
        mockServer.verify();
        consentServer.verify();
    }

    @Test
    void legal_consent_service_timeout_bypasses_with_trail() {
        createAndPublishTemplate("DSAR_REMINDER", "LEGAL");

        consentServer.expect(requestTo("http://consent-service/consent/check"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withServerError());

        mockServer.expect(requestTo("http://evidence-service/evidence"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"evidenceId\":\"evidence-consent-check\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("http://evidence-service/evidence"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"evidenceId\":\"evidence-consent-bypass\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("http://evidence-service/evidence"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"evidenceId\":\"evidence-sent\"}", MediaType.APPLICATION_JSON));

        when(notificationProvider.sendEmail(any()))
            .thenReturn(ProviderResult.success("MOCK", "msg-legal"));

        SendNotificationRequest request = new SendNotificationRequest(
            null,
            "DSAR_REMINDER",
            "en",
            "EMAIL",
            new SendNotificationRequest.Audience("DATA_PRINCIPAL", "dp-legal", null),
            Map.of("user_name", "Jane")
        );

        sendService.sendNotification(request);

        NotificationMessage message = messageRepository.findAll().get(0);
        assertThat(message.getStatus()).isEqualTo("SENT");

        Integer failedCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.audit_events WHERE action = 'NOTIFICATION_CONSENT_CHECK_FAILED'",
            Integer.class
        );
        Integer bypassCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.audit_events WHERE action = 'NOTIFICATION_BYPASSED_CONSENT_DUE_TO_LEGAL'",
            Integer.class
        );
        Integer sentCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.audit_events WHERE action = 'NOTIFICATION_SENT'",
            Integer.class
        );
        Integer failedOutbox = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.outbox_events WHERE event_type = 'NOTIFICATION_CONSENT_CHECK_FAILED'",
            Integer.class
        );
        Integer bypassOutbox = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.outbox_events WHERE event_type = 'NOTIFICATION_BYPASSED_CONSENT_DUE_TO_LEGAL'",
            Integer.class
        );
        assertThat(failedCount).isEqualTo(1);
        assertThat(bypassCount).isEqualTo(1);
        assertThat(sentCount).isEqualTo(1);
        assertThat(failedOutbox).isEqualTo(1);
        assertThat(bypassOutbox).isEqualTo(1);

        mockServer.verify();
        consentServer.verify();
    }

    @Test
    void retry_worker_does_not_send_when_consent_blocks() {
        TemplateIds ids = createTemplateAndVersion("GENERIC", "MARKETING");
        NotificationRequest request = createRequest(ids);
        NotificationMessage message = createMessage(request, ids, "FAILED_RETRYABLE", Instant.now().minusSeconds(5));

        consentServer.expect(requestTo("http://consent-service/consent/check"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withServerError());

        mockServer.expect(requestTo("http://evidence-service/evidence"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"evidenceId\":\"evidence-consent-failed\"}", MediaType.APPLICATION_JSON));

        retryWorkerService.runRetryBatch();

        NotificationMessage updated = messageRepository.findById(message.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("CONSENT_BLOCKED");
        assertThat(updated.getNextAttemptAt()).isNull();

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.audit_events WHERE action = 'NOTIFICATION_CONSENT_CHECK_FAILED'",
            Integer.class
        );
        Integer outboxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.outbox_events WHERE event_type = 'NOTIFICATION_CONSENT_CHECK_FAILED'",
            Integer.class
        );
        assertThat(auditCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);

        retryWorkerService.runRetryBatch();
        verify(notificationProvider, never()).sendEmail(any());
        mockServer.verify();
        consentServer.verify();
    }

    private UUID createAndPublishTemplate(String templateKey, String category) {
        NotificationTemplate template = new NotificationTemplate();
        template.setTenantId("00000000-0000-0000-0000-000000000001");
        template.setTemplateKey(templateKey);
        template.setCategory(category);
        template.setDefaultLanguage("en");
        template.setTitle("Test Template");
        template.setEnabled(true);
        template.setCreatedBy("test-user");
        template = templateRepository.save(template);

        NotificationTemplateVersion version = new NotificationTemplateVersion();
        version.setTenantId("00000000-0000-0000-0000-000000000001");
        version.setTemplateId(template.getTemplateId());
        version.setVersionNumber(1);
        version.setStatus("PUBLISHED");
        version.setCreatedBy("test-user");
        version = versionRepository.save(version);

        NotificationTemplateLanguage language = new NotificationTemplateLanguage();
        language.setTenantId("00000000-0000-0000-0000-000000000001");
        language.setVersionId(version.getVersionId());
        language.setLanguage("en");
        String subject = "Hello {{user_name}}";
        String body = "Body for {{user_name}}";
        language.setSubject(subject);
        language.setBody(body);
        language.setFormat("TEXT");
        language.setContentHash(computeContentHash(subject, body));
        language.setCreatedBy("test-user");
        languageRepository.save(language);

        template.setActiveVersionId(version.getVersionId());
        templateRepository.save(template);
        return template.getTemplateId();
    }

    private TemplateIds createTemplateAndVersion(String templateKey, String category) {
        UUID templateId = createAndPublishTemplate(templateKey, category);
        NotificationTemplate template = templateRepository.findById(templateId).orElseThrow();
        NotificationTemplateVersion version = versionRepository.findByTenantIdAndTemplateId(template.getTenantId(), templateId).get(0);
        return new TemplateIds(templateId, version.getVersionId(), template.getDefaultLanguage());
    }

    private NotificationRequest createRequest(TemplateIds ids) {
        NotificationRequest request = new NotificationRequest();
        request.setTenantId("00000000-0000-0000-0000-000000000001");
        request.setTemplateId(ids.templateId);
        request.setVersionId(ids.versionId);
        request.setLanguage("en");
        request.setChannel("EMAIL");
        request.setAudienceType("DATA_PRINCIPAL");
        request.setAudienceDataPrincipalId("dp-001");
        request.setVariables(Map.of("user_name", "Jane"));
        request.setTotalRecipients(1);
        request.setCreatedBy("tester");
        return requestRepository.save(request);
    }

    private NotificationMessage createMessage(NotificationRequest request, TemplateIds ids, String status, Instant nextAttemptAt) {
        String recipient = "dp-001@example.com";
        String hashInput = request.getRequestId() + "|" + recipient + "|" + ids.templateId + "|" + "MARKETING";
        byte[] messageHashBytes = sha256Bytes(hashInput);
        String messageHashHex = toHex(messageHashBytes);

        NotificationMessage message = new NotificationMessage();
        message.setTenantId(UUID.fromString(request.getTenantId()));
        message.setNotificationRequestId(request.getRequestId());
        message.setRecipient(recipient);
        message.setChannel("EMAIL");
        message.setCategory("MARKETING");
        message.setTemplateKey("GENERIC");
        message.setTemplateId(ids.templateId);
        message.setTemplateVersionId(ids.versionId);
        message.setLanguage(request.getLanguage());
        message.setMessageHash(messageHashBytes);
        message.setMessageHashHex(messageHashHex);
        message.setStatus(status);
        message.setNextAttemptAt(nextAttemptAt);
        return messageRepository.save(message);
    }

    private byte[] sha256Bytes(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String computeContentHash(String subject, String body) {
        return toHex(sha256Bytes(subject + body));
    }

    private String toHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static class TemplateIds {
        private final UUID templateId;
        private final UUID versionId;
        private final String defaultLanguage;

        private TemplateIds(UUID templateId, UUID versionId, String defaultLanguage) {
            this.templateId = templateId;
            this.versionId = versionId;
            this.defaultLanguage = defaultLanguage;
        }
    }
}
