package com.regulyn.notification.service;

import com.regulyn.notification.dto.*;
import com.regulyn.notification.entity.NotificationTemplate;
import com.regulyn.notification.entity.NotificationTemplateLanguage;
import com.regulyn.notification.entity.NotificationTemplateVersion;
import com.regulyn.notification.repository.NotificationTemplateLanguageRepository;
import com.regulyn.notification.repository.NotificationTemplateRepository;
import com.regulyn.notification.repository.NotificationTemplateVersionRepository;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.events.model.EventEnvelopeV1;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TemplateManagementService {
    
    private final NotificationTemplateRepository templateRepository;
    private final NotificationTemplateVersionRepository versionRepository;
    private final NotificationTemplateLanguageRepository languageRepository;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    
    public TemplateManagementService(
        NotificationTemplateRepository templateRepository,
        NotificationTemplateVersionRepository versionRepository,
        NotificationTemplateLanguageRepository languageRepository,
        AuditWriter auditWriter,
        OutboxWriter outboxWriter
    ) {
        this.templateRepository = templateRepository;
        this.versionRepository = versionRepository;
        this.languageRepository = languageRepository;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
    }
    
    @Transactional
    public CreateTemplateResponse createTemplate(CreateTemplateRequest request) {
        String tenantId = TenantContextHolder.getTenantId().toString();
        String userId = TenantContextHolder.getUserId().toString();
        
        // Check if template key already exists
        templateRepository.findByTenantIdAndTemplateKey(tenantId, request.templateKey())
            .ifPresent(t -> {
                throw new IllegalArgumentException("Template with key '" + request.templateKey() + "' already exists");
            });
        
        // Create template
        NotificationTemplate template = new NotificationTemplate();
        template.setTenantId(tenantId);
        template.setTemplateKey(request.templateKey());
        template.setCategory(request.category());
        template.setDefaultLanguage(request.defaultLanguage());
        template.setTitle(request.title());
        template.setEnabled(request.enabled() != null ? request.enabled() : true);
        template.setCreatedBy(userId);
        
        template = templateRepository.save(template);
        
        // Audit
        auditWriter.auditAction(
            "TEMPLATE_CREATED",
            "NotificationTemplate",
            template.getTemplateId().toString(),
            null
        );
        
        // Outbox event
        EventEnvelopeV1 event = new EventEnvelopeV1();
        event.setEventId(UUID.randomUUID());
        event.setTenantId(UUID.fromString(tenantId));
        event.setActorId(UUID.fromString(userId));
        event.setEventType("notification.template_created");
        event.setSourceService("notification-service");
        event.setEntityType("NotificationTemplate");
        event.setEntityId(template.getTemplateId().toString());
        event.setCorrelationId(TenantContextHolder.getRequestId());
        event.setOccurredAt(Instant.now());
        outboxWriter.write(event);
        
        return new CreateTemplateResponse(template.getTemplateId());
    }
    
    @Transactional
    public CreateVersionResponse createVersion(UUID templateId, CreateVersionRequest request) {
        String tenantId = TenantContextHolder.getTenantId().toString();
        String userId = TenantContextHolder.getUserId().toString();
        
        // Verify template exists
        NotificationTemplate template = templateRepository.findByTenantIdAndTemplateId(tenantId, templateId)
            .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        
        // Get next version number
        Integer maxVersion = versionRepository.findMaxVersionNumber(tenantId, templateId).orElse(0);
        int nextVersion = maxVersion + 1;
        
        // Create version
        NotificationTemplateVersion version = new NotificationTemplateVersion();
        version.setTenantId(tenantId);
        version.setTemplateId(templateId);
        version.setVersionNumber(nextVersion);
        version.setStatus("DRAFT");
        version.setChangeSummary(request.changeSummary());
        version.setCreatedBy(userId);
        
        version = versionRepository.save(version);
        
        // Audit
        auditWriter.auditAction(
            "TEMPLATE_VERSION_CREATED",
            "NotificationTemplateVersion",
            version.getVersionId().toString(),
            null
        );
        
        // Outbox event
        EventEnvelopeV1 event = new EventEnvelopeV1();
        event.setEventId(UUID.randomUUID());
        event.setTenantId(UUID.fromString(tenantId));
        event.setActorId(UUID.fromString(userId));
        event.setEventType("notification.template_version_created");
        event.setSourceService("notification-service");
        event.setEntityType("NotificationTemplateVersion");
        event.setEntityId(version.getVersionId().toString());
        event.setCorrelationId(TenantContextHolder.getRequestId());
        event.setOccurredAt(Instant.now());
        outboxWriter.write(event);
        
        return new CreateVersionResponse(version.getVersionId(), version.getVersionNumber(), version.getStatus());
    }
    
    @Transactional
    public AddLanguageResponse addLanguage(UUID versionId, AddLanguageRequest request) {
        String tenantId = TenantContextHolder.getTenantId().toString();
        String userId = TenantContextHolder.getUserId().toString();
        
        // Verify version exists
        NotificationTemplateVersion version = versionRepository.findByTenantIdAndVersionId(tenantId, versionId)
            .orElseThrow(() -> new IllegalArgumentException("Version not found"));
        
        // Check if version is DRAFT
        if (!"DRAFT".equals(version.getStatus())) {
            throw new IllegalStateException("Cannot add language to non-DRAFT version");
        }
        
        // Check if language already exists
        languageRepository.findByTenantIdAndVersionIdAndLanguage(tenantId, versionId, request.language())
            .ifPresent(l -> {
                throw new IllegalArgumentException("Language '" + request.language() + "' already exists for this version");
            });
        
        // Calculate content hash
        String contentHash = calculateContentHash(request.subject(), request.body());
        
        // Create language variant
        NotificationTemplateLanguage language = new NotificationTemplateLanguage();
        language.setTenantId(tenantId);
        language.setVersionId(versionId);
        language.setLanguage(request.language());
        language.setSubject(request.subject());
        language.setBody(request.body());
        language.setFormat(request.format());
        language.setContentHash(contentHash);
        language.setCreatedBy(userId);
        
        language = languageRepository.save(language);
        
        // Audit
        auditWriter.auditAction(
            "TEMPLATE_LANGUAGE_ADDED",
            "NotificationTemplateLanguage",
            language.getLanguageId().toString(),
            contentHash
        );
        
        // Outbox event
        EventEnvelopeV1 event = new EventEnvelopeV1();
        event.setEventId(UUID.randomUUID());
        event.setTenantId(UUID.fromString(tenantId));
        event.setActorId(UUID.fromString(userId));
        event.setEventType("notification.template_language_added");
        event.setSourceService("notification-service");
        event.setEntityType("NotificationTemplateLanguage");
        event.setEntityId(language.getLanguageId().toString());
        event.setCorrelationId(TenantContextHolder.getRequestId());
        event.setOccurredAt(Instant.now());
        outboxWriter.write(event);
        
        return new AddLanguageResponse(language.getLanguageId(), language.getContentHash());
    }
    
    @Transactional
    public PublishResponse publishVersion(UUID versionId, PublishRequest request) {
        String tenantId = TenantContextHolder.getTenantId().toString();
        
        // Verify version exists
        NotificationTemplateVersion version = versionRepository.findByTenantIdAndVersionId(tenantId, versionId)
            .orElseThrow(() -> new IllegalArgumentException("Version not found"));
        
        // Check if version is DRAFT
        if (!"DRAFT".equals(version.getStatus())) {
            throw new IllegalStateException("Only DRAFT versions can be published");
        }
        
        // Verify at least one language exists
        List<NotificationTemplateLanguage> languages = languageRepository.findByTenantIdAndVersionId(tenantId, versionId);
        if (languages.isEmpty()) {
            throw new IllegalStateException("Cannot publish version without any language variants");
        }
        
        // Retire currently published version
        versionRepository.retirePublishedVersions(tenantId, version.getTemplateId(), Instant.now());
        
        // Publish this version
        version.setStatus("PUBLISHED");
        version.setPublishedAt(Instant.now());
        if (request.changeSummary() != null && !request.changeSummary().isBlank()) {
            version.setChangeSummary(request.changeSummary());
        }
        version = versionRepository.save(version);
        
        // Update template's active_version_id
        NotificationTemplate template = templateRepository.findById(version.getTemplateId())
            .orElseThrow(() -> new IllegalStateException("Template not found"));
        template.setActiveVersionId(versionId);
        templateRepository.save(template);
        
        // Audit
        auditWriter.auditAction(
            "TEMPLATE_VERSION_PUBLISHED",
            "NotificationTemplateVersion",
            version.getVersionId().toString(),
            null
        );
        
        // Outbox event
        EventEnvelopeV1 event = new EventEnvelopeV1();
        event.setEventId(UUID.randomUUID());
        event.setTenantId(UUID.fromString(tenantId));
        event.setEventType("notification.template_published");
        event.setSourceService("notification-service");
        event.setEntityType("NotificationTemplateVersion");
        event.setEntityId(version.getVersionId().toString());
        event.setCorrelationId(TenantContextHolder.getRequestId());
        event.setOccurredAt(Instant.now());
        outboxWriter.write(event);
        
        return new PublishResponse(version.getVersionId(), version.getVersionNumber(), version.getStatus());
    }
    
    @Transactional(readOnly = true)
    public GetActiveTemplateResponse getActiveTemplate(String templateKey, String language) {
        String tenantId = TenantContextHolder.getTenantId().toString();
        
        // Find enabled template
        NotificationTemplate template = templateRepository.findEnabledByTenantIdAndTemplateKey(tenantId, templateKey)
            .orElseThrow(() -> new IllegalArgumentException("Active template not found for key: " + templateKey));
        
        // Get published version
        NotificationTemplateVersion version = versionRepository.findPublishedVersion(tenantId, template.getTemplateId())
            .orElseThrow(() -> new IllegalStateException("No published version found for template: " + templateKey));
        
        // Get all languages for this version
        List<NotificationTemplateLanguage> languages = languageRepository.findByTenantIdAndVersionId(tenantId, version.getVersionId());
        
        List<GetActiveTemplateResponse.LanguageVariant> languageVariants = languages.stream()
            .map(l -> new GetActiveTemplateResponse.LanguageVariant(
                l.getLanguage(),
                l.getSubject(),
                l.getBody(),
                l.getFormat(),
                l.getContentHash()
            ))
            .toList();
        
        return new GetActiveTemplateResponse(
            template.getTemplateId(),
            template.getTemplateKey(),
            template.getCategory(),
            template.getDefaultLanguage(),
            template.getTitle(),
            version.getVersionId(),
            version.getVersionNumber(),
            languageVariants
        );
    }
    
    private String calculateContentHash(String subject, String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String content = subject + body;
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            
            // Convert to hex
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
