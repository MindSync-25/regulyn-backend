package com.regulyn.notification.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.notification.config.TestSecurityConfig;
import com.regulyn.notification.dto.*;
import com.regulyn.notification.integration.EvidenceClient;
import com.regulyn.notification.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@Import(TestSecurityConfig.class)
class NotificationSendServiceTest {
    
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
        registry.add("consent.enabled", () -> "false");
    }
    
    @Autowired
    private NotificationSendService sendService;
    
    @Autowired
    private TemplateManagementService templateService;
    
    @Autowired
    private PreferenceService preferenceService;
    
    @Autowired
    private NotificationTemplateRepository templateRepository;
    
    @Autowired
    private NotificationTemplateVersionRepository versionRepository;
    
    @Autowired
    private NotificationTemplateLanguageRepository languageRepository;
    
    @Autowired
    private NotificationRequestRepository requestRepository;
    
    @Autowired
    private NotificationDispatchLogRepository dispatchLogRepository;

    @Autowired
    private NotificationMessageRepository messageRepository;
    
    @Autowired
    private CommunicationPreferenceRepository preferenceRepository;
    
    @MockBean
    private EvidenceClient evidenceClient;
    
    @BeforeEach
    void setUp() {
        TenantContext context = new TenantContext();
        context.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        context.setUserId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        context.setRequestId("request-001");
        TenantContextHolder.setContext(context);
        when(evidenceClient.createNotificationSentArtifact(any(), anyString()))
            .thenReturn("evidence-001");
    }
    
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        dispatchLogRepository.deleteAll();
        messageRepository.deleteAll();
        requestRepository.deleteAll();
        preferenceRepository.deleteAll();
        // Clear active_version_id FK references first
        templateRepository.findAll().forEach(t -> {
            t.setActiveVersionId(null);
            templateRepository.save(t);
        });
        languageRepository.deleteAll();
        versionRepository.deleteAll();
        templateRepository.deleteAll();
    }
    
    @Test
    @DirtiesContext
    void shouldSendNotificationWithVariableSubstitution() {
        // Given - create and publish template
        UUID templateId = createAndPublishTemplate();
        
        SendNotificationRequest request = new SendNotificationRequest(
            null,
            "DSAR_REMINDER",
            "en",
            "EMAIL",
            new SendNotificationRequest.Audience("DATA_PRINCIPAL", "dp-001", null),
            Map.of("user_name", "John Doe", "deadline", "2026-02-15")
        );
        
        // When
        SendNotificationResponse response = sendService.sendNotification(request);
        
        // Then
        assertThat(response.notificationRequestId()).isNotNull();
        assertThat(response.totalRecipients()).isEqualTo(1);
        assertThat(response.sentCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isEqualTo(0);
        assertThat(response.dispatches()).hasSize(1);
        assertThat(response.dispatches().get(0).status()).isEqualTo("SENT");
        
        // Verify dispatch log with variable substitution
        var dispatchLogs = dispatchLogRepository.findAll();
        assertThat(dispatchLogs).hasSize(1);
        assertThat(dispatchLogs.get(0).getMessageSubject()).contains("John Doe");
        assertThat(dispatchLogs.get(0).getMessageBody()).contains("2026-02-15");
    }
    
    @Test
    @DirtiesContext
    void shouldBlockMarketingWhenOptedOut() {
        // Given - create marketing template
        UUID templateId = createTemplate("GENERIC", "MARKETING");
        UUID versionId = createVersion(templateId);
        addLanguage(versionId, "Marketing Alert", "Special offer for {{user_name}}!");
        publishVersion(versionId);
        
        // Opt out from MARKETING
        preferenceService.updatePreference(new OptOutRequest("dp-001", "EMAIL", "MARKETING", true));
        
        SendNotificationRequest request = new SendNotificationRequest(
            null,
            "GENERIC",
            "en",
            "EMAIL",
            new SendNotificationRequest.Audience("DATA_PRINCIPAL", "dp-001", null),
            Map.of("user_name", "John Doe")
        );
        
        // When
        SendNotificationResponse response = sendService.sendNotification(request);
        
        // Then - should be skipped
        assertThat(response.sentCount()).isEqualTo(0);
        assertThat(response.skippedCount()).isEqualTo(1);
        assertThat(response.dispatches().get(0).status()).isEqualTo("CONSENT_BLOCKED");
        assertThat(response.dispatches().get(0).reason()).contains("OPTED_OUT");
        
        // Verify dispatch log
        var dispatchLogs = dispatchLogRepository.findAll();
        assertThat(dispatchLogs).hasSize(1);
        assertThat(dispatchLogs.get(0).getStatus()).isEqualTo("SKIPPED_OPT_OUT");
    }
    
    @DirtiesContext
    @Test
    void shouldAllowLegalEvenWhenOptedOut() {
        // Given - create legal template
        UUID templateId = createTemplate("BREACH_NOTICE", "LEGAL");
        UUID versionId = createVersion(templateId);
        addLanguage(versionId, "Security Breach Notice", "Important legal notice for {{user_name}}");
        publishVersion(versionId);
        
        // Opt out from LEGAL (should still allow)
        preferenceService.updatePreference(new OptOutRequest("dp-001", "EMAIL", "LEGAL", true));
        
        SendNotificationRequest request = new SendNotificationRequest(
            null,
            "BREACH_NOTICE",
            "en",
            "EMAIL",
            new SendNotificationRequest.Audience("DATA_PRINCIPAL", "dp-001", null),
            Map.of("user_name", "John Doe")
        );
        
        // When
        SendNotificationResponse response = sendService.sendNotification(request);
        
        // Then - should be sent despite opt-out
        assertThat(response.sentCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isEqualTo(0);
        assertThat(response.dispatches().get(0).status()).isEqualTo("SENT");
        
        // Verify dispatch log mentions opt-out but allowed
        var dispatchLogs = dispatchLogRepository.findAll();
        assertThat(dispatchLogs).hasSize(1);
        assertThat(dispatchLogs.get(0).getStatus()).isEqualTo("SENT");
        assertThat(dispatchLogs.get(0).getSkipReason()).contains("opted out").contains("allowed");
    }
    
    @DirtiesContext
    @Test
    void shouldReturnIdempotentReplayForDuplicateRequestRef() {
        // Given
        UUID templateId = createAndPublishTemplate();
        
        SendNotificationRequest request1 = new SendNotificationRequest(
            "unique-ref-001",
            "DSAR_REMINDER",
            "en",
            "EMAIL",
            new SendNotificationRequest.Audience("DATA_PRINCIPAL", "dp-001", null),
            null
        );
        
        // When - send first time
        SendNotificationResponse first = sendService.sendNotification(request1);
        
        // Then - second time should be idempotent replay
        SendNotificationRequest request2 = new SendNotificationRequest(
            "unique-ref-001",
            "DSAR_REMINDER",
            "en",
            "EMAIL",
            new SendNotificationRequest.Audience("DATA_PRINCIPAL", "dp-002", null),
            null
        );
        
        SendNotificationResponse replay = sendService.sendNotification(request2);
        assertThat(replay.notificationRequestId()).isEqualTo(first.notificationRequestId());
        assertThat(replay.dispatches()).isEmpty();
    }
    
    @DirtiesContext
    @Test
    void shouldHandleUserIdsAudience() {
        // Given
        UUID templateId = createAndPublishTemplate();
        
        SendNotificationRequest request = new SendNotificationRequest(
            null,
            "DSAR_REMINDER",
            "en",
            "EMAIL",
            new SendNotificationRequest.Audience("USER_IDS", null, List.of("user-1", "user-2", "user-3")),
            Map.of("user_name", "Team", "deadline", "2026-02-01")
        );
        
        // When
        SendNotificationResponse response = sendService.sendNotification(request);
        
        // Then
        assertThat(response.totalRecipients()).isEqualTo(3);
        assertThat(response.sentCount()).isEqualTo(3);
        assertThat(response.dispatches()).hasSize(3);
    }
    
    @DirtiesContext
    @Test
    void shouldFallbackToDefaultLanguage() {
        // Given - template with only English
        UUID templateId = createAndPublishTemplate();
        
        // Request in French (not available)
        SendNotificationRequest request = new SendNotificationRequest(
            null,
            "DSAR_REMINDER",
            "fr",
            "EMAIL",
            new SendNotificationRequest.Audience("DATA_PRINCIPAL", "dp-001", null),
            Map.of("user_name", "Jean Dupont", "deadline", "2026-02-15")
        );
        
        // When
        SendNotificationResponse response = sendService.sendNotification(request);
        
        // Then - should fallback to English
        assertThat(response.sentCount()).isEqualTo(1);
        
        var dispatchLogs = dispatchLogRepository.findAll();
        assertThat(dispatchLogs.get(0).getMessageSubject()).contains("DSAR Request");
    }
    
    // Helper methods
    
    private UUID createAndPublishTemplate() {
        UUID templateId = createTemplate("DSAR_REMINDER", "LEGAL");
        UUID versionId = createVersion(templateId);
        addLanguage(versionId, "DSAR Request Reminder for {{user_name}}", 
                    "Dear {{user_name}}, this is a reminder about your DSAR request. Deadline: {{deadline}}");
        publishVersion(versionId);
        return templateId;
    }
    
    private UUID createTemplate(String key, String category) {
        CreateTemplateRequest request = new CreateTemplateRequest(
            key, category, "en", key + " Template", true
        );
        return templateService.createTemplate(request).templateId();
    }
    
    private UUID createVersion(UUID templateId) {
        CreateVersionRequest request = new CreateVersionRequest("Initial version");
        return templateService.createVersion(templateId, request).versionId();
    }
    
    private void addLanguage(UUID versionId, String subject, String body) {
        AddLanguageRequest request = new AddLanguageRequest("en", subject, body, "TEXT");
        templateService.addLanguage(versionId, request);
    }
    
    private void publishVersion(UUID versionId) {
        templateService.publishVersion(versionId, new PublishRequest(null));
    }
}
