package com.regulyn.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.apikey.ApiKeyValidator;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.notification.dto.DeliveryCallbackRequest;
import com.regulyn.notification.entity.NotificationDeliveryReceipt;
import com.regulyn.notification.entity.NotificationMessage;
import com.regulyn.notification.entity.NotificationRequest;
import com.regulyn.notification.entity.NotificationTemplate;
import com.regulyn.notification.entity.NotificationTemplateLanguage;
import com.regulyn.notification.entity.NotificationTemplateVersion;
import com.regulyn.notification.repository.NotificationDeliveryReceiptRepository;
import com.regulyn.notification.repository.NotificationMessageRepository;
import com.regulyn.notification.repository.NotificationRequestRepository;
import com.regulyn.notification.repository.NotificationTemplateLanguageRepository;
import com.regulyn.notification.repository.NotificationTemplateRepository;
import com.regulyn.notification.repository.NotificationTemplateVersionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc(addFilters = false)
@AutoConfigureMockRestServiceServer
class NotificationRound2Part3IT {

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
        registry.add("consent.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationMessageRepository messageRepository;

    @Autowired
    private NotificationDeliveryReceiptRepository receiptRepository;

    @Autowired
    private NotificationRequestRepository requestRepository;

    @Autowired
    private NotificationTemplateRepository templateRepository;

    @Autowired
    private NotificationTemplateVersionRepository versionRepository;

    @Autowired
    private NotificationTemplateLanguageRepository languageRepository;

    @Autowired
    private MockRestServiceServer mockServer;

    @MockBean
    private ApiKeyValidator apiKeyValidator;

    private static final UUID TENANT_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        TenantContext context = new TenantContext();
        context.setTenantId(TENANT_UUID);
        context.setUserId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        context.setRequestId("request-callback-001");
        TenantContextHolder.setContext(context);

        when(apiKeyValidator.validate(anyString()))
            .thenReturn(ApiKeyValidator.ApiKeyValidationResult.valid(UUID.randomUUID(), "test"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        jdbcTemplate.update("DELETE FROM notification.outbox_events");
        jdbcTemplate.update("DELETE FROM notification.audit_events");
        jdbcTemplate.update("DELETE FROM notification.notification_delivery_receipts");
        jdbcTemplate.update("DELETE FROM notification.notification_messages");
        jdbcTemplate.update("DELETE FROM notification.notification_dispatch_logs");
        jdbcTemplate.update("DELETE FROM notification.notification_requests");
        templateRepository.findAll().forEach(t -> {
            t.setActiveVersionId(null);
            templateRepository.save(t);
        });
        languageRepository.deleteAll();
        versionRepository.deleteAll();
        templateRepository.deleteAll();
    }

    @Test
    void callback_persists_receipt_and_updates_message_to_delivered_and_stores_evidence_ref() throws Exception {
        TemplateIds ids = createTemplateAndVersion("DSAR_REMINDER", "LEGAL");
        NotificationRequest request = createRequest(ids);
        NotificationMessage message = createMessage(request, ids);

        mockServer.expect(requestTo("http://evidence-service/evidence"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"evidenceId\":\"evidence-delivered\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("http://evidence-service/evidence"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"evidenceId\":\"evidence-receipt\"}", MediaType.APPLICATION_JSON));

        DeliveryCallbackRequest payload = new DeliveryCallbackRequest(
            TENANT_UUID,
            message.getId(),
            "SMTP",
            "smtp-001",
            "DELIVERED",
            Instant.now(),
            null,
            objectMapper.createObjectNode().put("event", "delivered")
        );

        mockMvc.perform(post("/api/notifications/callbacks/smtp/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deduped").value(false));

        NotificationDeliveryReceipt receipt = receiptRepository.findAll().get(0);
        assertThat(receipt.getPayloadHashHex()).isNotBlank();

        NotificationMessage updated = messageRepository.findById(message.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("DELIVERED");
        assertThat(updated.getDeliveredEvidenceArtifactRef()).isNotBlank();

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.audit_events WHERE action = 'NOTIFICATION_DELIVERY_UPDATED'",
            Integer.class
        );
        Integer outboxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.outbox_events WHERE event_type = 'NOTIFICATION_DELIVERY_UPDATED'",
            Integer.class
        );
        assertThat(auditCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);

        mockServer.verify();
    }

    @Test
    void callback_idempotency_duplicate_payload_hash_is_deduped() throws Exception {
        TemplateIds ids = createTemplateAndVersion("DSAR_REMINDER", "LEGAL");
        NotificationRequest request = createRequest(ids);
        NotificationMessage message = createMessage(request, ids);

        mockServer.expect(requestTo("http://evidence-service/evidence"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"evidenceId\":\"evidence-delivered\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("http://evidence-service/evidence"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"evidenceId\":\"evidence-receipt\"}", MediaType.APPLICATION_JSON));

        DeliveryCallbackRequest payload = new DeliveryCallbackRequest(
            TENANT_UUID,
            message.getId(),
            "SMTP",
            "smtp-dup",
            "DELIVERED",
            Instant.now(),
            null,
            objectMapper.createObjectNode().put("event", "delivered")
        );

        mockMvc.perform(post("/api/notifications/callbacks/smtp/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deduped").value(false));

        mockMvc.perform(post("/api/notifications/callbacks/smtp/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deduped").value(true));

        assertThat(receiptRepository.findAll()).hasSize(1);
        NotificationMessage updated = messageRepository.findById(message.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("DELIVERED");

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.audit_events WHERE action = 'NOTIFICATION_DELIVERY_UPDATED'",
            Integer.class
        );
        assertThat(auditCount).isGreaterThanOrEqualTo(2);

        mockServer.verify();
    }

    @Test
    void callback_failure_marks_failed_terminal_and_creates_failed_evidence() throws Exception {
        TemplateIds ids = createTemplateAndVersion("DSAR_REMINDER", "LEGAL");
        NotificationRequest request = createRequest(ids);
        NotificationMessage message = createMessage(request, ids);

        mockServer.expect(requestTo("http://evidence-service/evidence"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"evidenceId\":\"evidence-failed\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("http://evidence-service/evidence"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"evidenceId\":\"evidence-receipt\"}", MediaType.APPLICATION_JSON));

        DeliveryCallbackRequest payload = new DeliveryCallbackRequest(
            TENANT_UUID,
            message.getId(),
            "SMTP",
            "smtp-fail",
            "FAILED",
            Instant.now(),
            "Mailbox not found",
            objectMapper.createObjectNode().put("event", "failed")
        );

        mockMvc.perform(post("/api/notifications/callbacks/smtp/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deduped").value(false));

        NotificationMessage updated = messageRepository.findById(message.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("FAILED_TERMINAL");
        assertThat(updated.getFailedEvidenceArtifactRef()).isNotBlank();
        assertThat(updated.getLastFailureReason()).contains("Mailbox not found");

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.audit_events WHERE action = 'NOTIFICATION_FAILED_TERMINAL'",
            Integer.class
        );
        Integer outboxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.outbox_events WHERE event_type = 'NOTIFICATION_FAILED_TERMINAL'",
            Integer.class
        );
        assertThat(auditCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);

        mockServer.verify();
    }

    private TemplateIds createTemplateAndVersion(String templateKey, String category) {
        NotificationTemplate template = new NotificationTemplate();
        template.setTenantId(TENANT_UUID.toString());
        template.setTemplateKey(templateKey);
        template.setCategory(category);
        template.setDefaultLanguage("en");
        template.setTitle("Test Template");
        template.setEnabled(true);
        template.setCreatedBy("test-user");
        template = templateRepository.save(template);

        NotificationTemplateVersion version = new NotificationTemplateVersion();
        version.setTenantId(TENANT_UUID.toString());
        version.setTemplateId(template.getTemplateId());
        version.setVersionNumber(1);
        version.setStatus("PUBLISHED");
        version.setCreatedBy("test-user");
        version = versionRepository.save(version);

        NotificationTemplateLanguage language = new NotificationTemplateLanguage();
        language.setTenantId(TENANT_UUID.toString());
        language.setVersionId(version.getVersionId());
        language.setLanguage("en");
        String subject = "Hello {{user_name}}";
        String body = "Body for {{user_name}}";
        language.setSubject(subject);
        language.setBody(body);
        language.setFormat("TEXT");
        language.setContentHash(calculateContentHash(subject, body));
        language.setCreatedBy("test-user");
        languageRepository.save(language);

        template.setActiveVersionId(version.getVersionId());
        templateRepository.save(template);

        return new TemplateIds(template.getTemplateId(), version.getVersionId());
    }

    private NotificationRequest createRequest(TemplateIds ids) {
        NotificationRequest request = new NotificationRequest();
        request.setTenantId(TENANT_UUID.toString());
        request.setTemplateId(ids.templateId());
        request.setVersionId(ids.versionId());
        request.setLanguage("en");
        request.setChannel("EMAIL");
        request.setAudienceType("DATA_PRINCIPAL");
        request.setAudienceDataPrincipalId("dp-001");
        request.setVariables(Map.of("user_name", "John Doe"));
        request.setTotalRecipients(1);
        request.setCreatedBy("test-user");
        return requestRepository.save(request);
    }

    private NotificationMessage createMessage(NotificationRequest request, TemplateIds ids) {
        byte[] hash = sha256Bytes("callback-test".getBytes(StandardCharsets.UTF_8));
        NotificationMessage message = new NotificationMessage();
        message.setTenantId(TENANT_UUID);
        message.setNotificationRequestId(request.getRequestId());
        message.setRecipient("dp-001@example.com");
        message.setChannel("EMAIL");
        message.setCategory("LEGAL");
        message.setTemplateKey("DSAR_REMINDER");
        message.setTemplateId(ids.templateId());
        message.setTemplateVersionId(ids.versionId());
        message.setLanguage("en");
        message.setMessageHash(hash);
        message.setMessageHashHex(toHex(hash));
        message.setStatus("SENT");
        return messageRepository.save(message);
    }

    private String calculateContentHash(String subject, String body) {
        return toHex(sha256Bytes((subject + body).getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] sha256Bytes(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
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

    private record TemplateIds(UUID templateId, UUID versionId) {
    }
}
