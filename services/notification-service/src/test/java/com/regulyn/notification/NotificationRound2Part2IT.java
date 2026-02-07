package com.regulyn.notification;

import com.regulyn.auth.apikey.ApiKeyValidator;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.notification.dto.SendNotificationRequest;
import com.regulyn.notification.dto.SendNotificationResponse;
import com.regulyn.notification.entity.NotificationMessage;
import com.regulyn.notification.entity.NotificationTemplate;
import com.regulyn.notification.entity.NotificationTemplateLanguage;
import com.regulyn.notification.entity.NotificationTemplateVersion;
import com.regulyn.notification.provider.NotificationProvider;
import com.regulyn.notification.provider.ProviderResult;
import com.regulyn.notification.repository.NotificationMessageRepository;
import com.regulyn.notification.repository.NotificationTemplateLanguageRepository;
import com.regulyn.notification.repository.NotificationTemplateRepository;
import com.regulyn.notification.repository.NotificationTemplateVersionRepository;
import com.regulyn.notification.service.NotificationSendService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest
@Testcontainers
@AutoConfigureMockRestServiceServer
class NotificationRound2Part2IT {
    
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
        registry.add("smtp.enabled", () -> "false");
        registry.add("consent.enabled", () -> "false");
    }
    
    @Autowired
    private NotificationSendService sendService;
    
    @Autowired
    private NotificationMessageRepository messageRepository;
    
    @Autowired
    private NotificationTemplateRepository templateRepository;
    
    @Autowired
    private NotificationTemplateVersionRepository versionRepository;
    
    @Autowired
    private NotificationTemplateLanguageRepository languageRepository;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private MockRestServiceServer mockServer;
    
    @MockBean
    private NotificationProvider notificationProvider;

    @MockBean
    private ApiKeyValidator apiKeyValidator;
    
    @BeforeEach
    void setUp() {
        TenantContext context = new TenantContext();
        context.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        context.setUserId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        context.setRequestId("request-002");
        TenantContextHolder.setContext(context);
        
        when(notificationProvider.getChannel()).thenReturn("EMAIL");
        when(notificationProvider.getProviderName()).thenReturn("MOCK");
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
        messageRepository.deleteAll();
    }
    
    @Test
    void sending_creates_notification_message_and_audit_outbox_and_sent_evidence_ref() {
        UUID templateId = createAndPublishTemplate("DSAR_REMINDER", "LEGAL");
        
        mockServer.expect(requestTo("http://evidence-service/evidence"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"evidenceId\":\"evidence-123\",\"status\":\"CREATED\"}", MediaType.APPLICATION_JSON));
        
        when(notificationProvider.sendEmail(any()))
            .thenReturn(ProviderResult.success("MOCK", "msg-1"));
        
        SendNotificationRequest request = new SendNotificationRequest(
            null,
            "DSAR_REMINDER",
            "en",
            "EMAIL",
            new SendNotificationRequest.Audience("DATA_PRINCIPAL", "dp-001", null),
            Map.of("user_name", "John Doe")
        );
        
        SendNotificationResponse response = sendService.sendNotification(request);
        assertThat(response.sentCount()).isEqualTo(1);
        
        NotificationMessage message = messageRepository.findAll().get(0);
        assertThat(message.getStatus()).isEqualTo("SENT");
        assertThat(message.getSentEvidenceArtifactRef()).isEqualTo("evidence-123");
        
        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.audit_events WHERE action = 'NOTIFICATION_SENT'",
            Integer.class
        );
        assertThat(auditCount).isEqualTo(1);
        
        Integer outboxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.outbox_events WHERE event_type = 'NOTIFICATION_SENT'",
            Integer.class
        );
        assertThat(outboxCount).isEqualTo(1);
        
        mockServer.verify();
    }
    
    @Test
    void idempotent_duplicate_send_does_not_send_twice() {
        UUID templateId = createAndPublishTemplate("DSAR_REMINDER", "LEGAL");
        
        mockServer.expect(requestTo("http://evidence-service/evidence"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"evidenceId\":\"evidence-456\",\"status\":\"CREATED\"}", MediaType.APPLICATION_JSON));
        
        when(notificationProvider.sendEmail(any()))
            .thenReturn(ProviderResult.success("MOCK", "msg-2"));
        
        SendNotificationRequest request = new SendNotificationRequest(
            "dup-ref-001",
            "DSAR_REMINDER",
            "en",
            "EMAIL",
            new SendNotificationRequest.Audience("DATA_PRINCIPAL", "dp-001", null),
            Map.of("user_name", "John Doe")
        );
        
        sendService.sendNotification(request);
        SendNotificationResponse replay = sendService.sendNotification(request);
        
        verify(notificationProvider, times(1)).sendEmail(any());
        assertThat(messageRepository.findAll()).hasSize(1);
        
        Integer idempotentCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.audit_events WHERE action = 'NOTIFICATION_IDEMPOTENT_REPLAY'",
            Integer.class
        );
        assertThat(idempotentCount).isGreaterThanOrEqualTo(1);
        
        assertThat(replay.sentCount()).isEqualTo(1);
        mockServer.verify();
    }
    
    @Test
    void failure_marks_failed_retryable_and_sets_next_attempt_at() {
        UUID templateId = createAndPublishTemplate("DSAR_REMINDER", "LEGAL");
        
        when(notificationProvider.sendEmail(any()))
            .thenReturn(ProviderResult.failure("MOCK", "Transient SMTP failure", true));
        
        SendNotificationRequest request = new SendNotificationRequest(
            null,
            "DSAR_REMINDER",
            "en",
            "EMAIL",
            new SendNotificationRequest.Audience("DATA_PRINCIPAL", "dp-001", null),
            Map.of("user_name", "John Doe")
        );
        
        sendService.sendNotification(request);
        
        NotificationMessage message = messageRepository.findAll().get(0);
        assertThat(message.getStatus()).isEqualTo("FAILED_RETRYABLE");
        assertThat(message.getNextAttemptAt()).isNotNull();
        assertThat(message.getNextAttemptAt()).isAfter(Instant.now().minusSeconds(5));
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
        language.setContentHash(calculateContentHash(subject, body));
        language.setCreatedBy("test-user");
        languageRepository.save(language);
        
        template.setActiveVersionId(version.getVersionId());
        templateRepository.save(template);
        
        return template.getTemplateId();
    }

    private String calculateContentHash(String subject, String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((subject + body).getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}