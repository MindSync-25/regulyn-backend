package com.regulyn.notification.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.notification.config.TestSecurityConfig;
import com.regulyn.notification.dto.*;
import com.regulyn.notification.repository.NotificationTemplateLanguageRepository;
import com.regulyn.notification.repository.NotificationTemplateRepository;
import com.regulyn.notification.repository.NotificationTemplateVersionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Import(TestSecurityConfig.class)
class TemplateManagementServiceTest {
    
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
    }
    
    @Autowired
    private TemplateManagementService templateService;
    
    @Autowired
    private NotificationTemplateRepository templateRepository;
    
    @Autowired
    private NotificationTemplateVersionRepository versionRepository;
    
    @Autowired
    private NotificationTemplateLanguageRepository languageRepository;
    
    private static final String TENANT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String USER_ID = "00000000-0000-0000-0000-000000000002";
    private static final String REQUEST_ID = "request-001";
    
    @BeforeEach
    void setUp() {
        TenantContext context = new TenantContext();
        context.setTenantId(UUID.fromString(TENANT_ID));
        context.setUserId(UUID.fromString(USER_ID));
        context.setRequestId(REQUEST_ID);
        TenantContextHolder.setContext(context);
    }
    
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
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
    void shouldCreateTemplate() {
        // Given
        CreateTemplateRequest request = new CreateTemplateRequest(
            "BREACH_NOTICE",
            "LEGAL",
            "en",
            "Data Breach Notification",
            true
        );
        
        // When
        CreateTemplateResponse response = templateService.createTemplate(request);
        
        // Then
        assertThat(response.templateId()).isNotNull();
        
        var template = templateRepository.findById(response.templateId()).orElseThrow();
        assertThat(template.getTemplateKey()).isEqualTo("BREACH_NOTICE");
        assertThat(template.getCategory()).isEqualTo("LEGAL");
        assertThat(template.getDefaultLanguage()).isEqualTo("en");
        assertThat(template.getTitle()).isEqualTo("Data Breach Notification");
        assertThat(template.getEnabled()).isTrue();
        assertThat(template.getTenantId()).isEqualTo(TENANT_ID);
    }
    
    @Test
    void shouldPreventDuplicateTemplateKey() {
        // Given
        CreateTemplateRequest request = new CreateTemplateRequest(
            "BREACH_NOTICE",
            "LEGAL",
            "en",
            "Data Breach Notification",
            true
        );
        templateService.createTemplate(request);
        
        // When/Then
        assertThatThrownBy(() -> templateService.createTemplate(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }
    
    @Test
    void shouldCreateVersionForTemplate() {
        // Given
        UUID templateId = createTemplate();
        CreateVersionRequest versionRequest = new CreateVersionRequest("Initial version");
        
        // When
        CreateVersionResponse response = templateService.createVersion(templateId, versionRequest);
        
        // Then
        assertThat(response.versionId()).isNotNull();
        assertThat(response.versionNumber()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("DRAFT");
        
        var version = versionRepository.findById(response.versionId()).orElseThrow();
        assertThat(version.getTemplateId()).isEqualTo(templateId);
        assertThat(version.getChangeSummary()).isEqualTo("Initial version");
    }
    
    @Test
    void shouldIncrementVersionNumber() {
        // Given
        UUID templateId = createTemplate();
        templateService.createVersion(templateId, new CreateVersionRequest("V1"));
        
        // When
        CreateVersionResponse v2 = templateService.createVersion(templateId, new CreateVersionRequest("V2"));
        
        // Then
        assertThat(v2.versionNumber()).isEqualTo(2);
    }
    
    @Test
    void shouldAddLanguageToVersion() {
        // Given
        UUID templateId = createTemplate();
        CreateVersionResponse version = templateService.createVersion(templateId, new CreateVersionRequest("Initial"));
        
        AddLanguageRequest languageRequest = new AddLanguageRequest(
            "en",
            "Breach Notice: {{incident_id}}",
            "Dear {{user_name}}, we detected a breach on {{date}}.",
            "TEXT"
        );
        
        // When
        AddLanguageResponse response = templateService.addLanguage(version.versionId(), languageRequest);
        
        // Then
        assertThat(response.languageId()).isNotNull();
        assertThat(response.contentHash()).isNotNull();
        assertThat(response.contentHash()).hasSize(64); // SHA-256 hex
        
        var language = languageRepository.findById(response.languageId()).orElseThrow();
        assertThat(language.getLanguage()).isEqualTo("en");
        assertThat(language.getSubject()).contains("{{incident_id}}");
        assertThat(language.getBody()).contains("{{user_name}}");
    }
    
    @Test
    void shouldPreventDuplicateLanguage() {
        // Given
        UUID templateId = createTemplate();
        CreateVersionResponse version = templateService.createVersion(templateId, new CreateVersionRequest("Initial"));
        
        AddLanguageRequest languageRequest = new AddLanguageRequest(
            "en",
            "Subject",
            "Body",
            "TEXT"
        );
        templateService.addLanguage(version.versionId(), languageRequest);
        
        // When/Then
        assertThatThrownBy(() -> templateService.addLanguage(version.versionId(), languageRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }
    
    @Test
    void shouldPreventAddingLanguageToPublishedVersion() {
        // Given
        UUID templateId = createTemplate();
        CreateVersionResponse version = templateService.createVersion(templateId, new CreateVersionRequest("Initial"));
        templateService.addLanguage(version.versionId(), new AddLanguageRequest("en", "Subject", "Body", "TEXT"));
        templateService.publishVersion(version.versionId(), new PublishRequest(null));
        
        // When/Then
        AddLanguageRequest newLanguage = new AddLanguageRequest("fr", "Sujet", "Corps", "TEXT");
        assertThatThrownBy(() -> templateService.addLanguage(version.versionId(), newLanguage))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("non-DRAFT");
    }
    
    @Test
    void shouldPublishVersion() {
        // Given
        UUID templateId = createTemplate();
        CreateVersionResponse version = templateService.createVersion(templateId, new CreateVersionRequest("Initial"));
        templateService.addLanguage(version.versionId(), new AddLanguageRequest("en", "Subject", "Body", "TEXT"));
        
        // When
        PublishResponse response = templateService.publishVersion(version.versionId(), new PublishRequest("Ready for production"));
        
        // Then
        assertThat(response.status()).isEqualTo("PUBLISHED");
        
        var publishedVersion = versionRepository.findById(response.versionId()).orElseThrow();
        assertThat(publishedVersion.getStatus()).isEqualTo("PUBLISHED");
        assertThat(publishedVersion.getPublishedAt()).isNotNull();
        
        var template = templateRepository.findById(templateId).orElseThrow();
        assertThat(template.getActiveVersionId()).isEqualTo(version.versionId());
    }
    
    @Test
    void shouldRetirePreviousVersionWhenPublishing() {
        // Given
        UUID templateId = createTemplate();
        
        // Create and publish V1
        CreateVersionResponse v1 = templateService.createVersion(templateId, new CreateVersionRequest("V1"));
        templateService.addLanguage(v1.versionId(), new AddLanguageRequest("en", "V1 Subject", "V1 Body", "TEXT"));
        templateService.publishVersion(v1.versionId(), new PublishRequest(null));
        
        // Create and publish V2
        CreateVersionResponse v2 = templateService.createVersion(templateId, new CreateVersionRequest("V2"));
        templateService.addLanguage(v2.versionId(), new AddLanguageRequest("en", "V2 Subject", "V2 Body", "TEXT"));
        templateService.publishVersion(v2.versionId(), new PublishRequest(null));
        
        // When/Then
        var v1Entity = versionRepository.findById(v1.versionId()).orElseThrow();
        assertThat(v1Entity.getStatus()).isEqualTo("RETIRED");
        assertThat(v1Entity.getRetiredAt()).isNotNull();
        
        var v2Entity = versionRepository.findById(v2.versionId()).orElseThrow();
        assertThat(v2Entity.getStatus()).isEqualTo("PUBLISHED");
    }
    
    @Test
    void shouldGetActiveTemplate() {
        // Given
        UUID templateId = createTemplate();
        CreateVersionResponse version = templateService.createVersion(templateId, new CreateVersionRequest("V1"));
        templateService.addLanguage(version.versionId(), new AddLanguageRequest("en", "English Subject", "English Body", "TEXT"));
        templateService.addLanguage(version.versionId(), new AddLanguageRequest("fr", "French Subject", "French Body", "HTML"));
        templateService.publishVersion(version.versionId(), new PublishRequest(null));
        
        // When
        GetActiveTemplateResponse response = templateService.getActiveTemplate("BREACH_NOTICE", "en");
        
        // Then
        assertThat(response.templateKey()).isEqualTo("BREACH_NOTICE");
        assertThat(response.category()).isEqualTo("LEGAL");
        assertThat(response.activeVersionNumber()).isEqualTo(1);
        assertThat(response.languages()).hasSize(2);
        assertThat(response.languages()).extracting("language").containsExactlyInAnyOrder("en", "fr");
    }
    
    @Test
    void shouldThrowWhenTemplateNotPublished() {
        // Given
        UUID templateId = createTemplate();
        CreateVersionResponse version = templateService.createVersion(templateId, new CreateVersionRequest("V1"));
        templateService.addLanguage(version.versionId(), new AddLanguageRequest("en", "Subject", "Body", "TEXT"));
        // Not published
        
        // When/Then
        assertThatThrownBy(() -> templateService.getActiveTemplate("BREACH_NOTICE", "en"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No published version");
    }
    
    private UUID createTemplate() {
        CreateTemplateRequest request = new CreateTemplateRequest(
            "BREACH_NOTICE",
            "LEGAL",
            "en",
            "Data Breach Notification",
            true
        );
        return templateService.createTemplate(request).templateId();
    }
}
