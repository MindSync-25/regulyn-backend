package com.regulyn.incident.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.incident.entity.NoticeTemplateEntity;
import com.regulyn.incident.entity.NoticeTemplateVersionEntity;
import com.regulyn.incident.events.AuditOutboxWriter;
import com.regulyn.incident.exception.TemplateNotFoundException;
import com.regulyn.incident.exception.TemplateVariableNotAllowedException;
import com.regulyn.incident.exception.VersionNotFoundException;
import com.regulyn.incident.repository.NoticeTemplateRepository;
import com.regulyn.incident.repository.NoticeTemplateVersionRepository;
import com.regulyn.incident.util.HashingUtil;
import com.regulyn.incident.util.TemplateRenderer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class NoticeTemplateService {

    private final NoticeTemplateRepository templateRepository;
    private final NoticeTemplateVersionRepository versionRepository;
    private final AuditOutboxWriter auditOutboxWriter;
    private final ObjectMapper objectMapper;

    public NoticeTemplateService(
            NoticeTemplateRepository templateRepository,
            NoticeTemplateVersionRepository versionRepository,
            AuditOutboxWriter auditOutboxWriter,
            ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.versionRepository = versionRepository;
        this.auditOutboxWriter = auditOutboxWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NoticeTemplateEntity createTemplate(UUID tenantId, String templateType, String name, String description, UUID actorId) {
        Optional<NoticeTemplateEntity> existing = templateRepository
                .findByTenantIdAndTemplateTypeAndName(tenantId, templateType, name);
        if (existing.isPresent()) {
            return existing.get();
        }

        NoticeTemplateEntity template = new NoticeTemplateEntity();
        template.setTenantId(tenantId);
        template.setTemplateType(templateType);
        template.setName(name);
        template.setDescription(description);
        template.setCreatedBy(actorId != null ? actorId.toString() : "system");
        template.setCreatedAt(Instant.now());
        template.setIsActive(true);

        try {
            template = templateRepository.save(template);
        } catch (DataIntegrityViolationException ex) {
            return templateRepository
                    .findByTenantIdAndTemplateTypeAndName(tenantId, templateType, name)
                    .orElseThrow(() -> ex);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("tenant_id", tenantId.toString());
        payload.put("template_id", template.getId().toString());
        payload.put("template_type", templateType);
        payload.put("name", name);
        payload.put("created_by", template.getCreatedBy());
        payload.put("created_at", template.getCreatedAt().toString());

        auditOutboxWriter.publish(
                "NOTICE_TEMPLATE_CREATED",
                "notice_template",
                template.getId().toString(),
                tenantId,
                actorId,
                payload
        );

        return template;
    }

    @Transactional
    public NoticeTemplateVersionEntity createTemplateVersion(UUID tenantId,
                                                             UUID templateId,
                                                             String language,
                                                             String content,
                                                             String variablesSchemaJson,
                                                             UUID actorId) {
        NoticeTemplateEntity template = templateRepository.findById(templateId)
            .orElseThrow(() -> new TemplateNotFoundException("Template not found"));

        int nextVersion = Optional.ofNullable(versionRepository.findMaxVersionForTemplate(templateId)).orElse(0) + 1;

        Set<String> placeholders = TemplateRenderer.extractPlaceholders(content);
        validateAllowedVariables(placeholders, parseAllowedVariables(variablesSchemaJson));

        NoticeTemplateVersionEntity version = new NoticeTemplateVersionEntity();
        version.setTenantId(tenantId);
        version.setTemplateId(templateId);
        version.setVersion(nextVersion);
        version.setLanguage(language);
        version.setContent(content);
        version.setContentSha256(HashingUtil.sha256Hex(content));
        version.setVariablesSchema(variablesSchemaJson == null ? "[]" : variablesSchemaJson);
        version.setCreatedBy(actorId != null ? actorId.toString() : "system");
        version.setCreatedAt(Instant.now());
        version.setIsRetired(false);

        version = versionRepository.save(version);

        Map<String, Object> payload = new HashMap<>();
        payload.put("tenant_id", tenantId.toString());
        payload.put("template_id", templateId.toString());
        payload.put("version_id", version.getId().toString());
        payload.put("template_type", template.getTemplateType());
        payload.put("language", language);
        payload.put("version", version.getVersion());
        payload.put("content_sha256", version.getContentSha256());
        payload.put("created_by", version.getCreatedBy());
        payload.put("created_at", version.getCreatedAt().toString());

        auditOutboxWriter.publish(
                "NOTICE_TEMPLATE_VERSIONED",
                "notice_template_version",
                version.getId().toString(),
                tenantId,
                actorId,
                payload
        );

        return version;
    }

    @Transactional(readOnly = true)
    public NoticeTemplateVersionEntity getTemplateVersion(UUID versionId) {
        return versionRepository.findById(versionId)
                .orElseThrow(() -> new VersionNotFoundException("Template version not found"));
    }

    private Set<String> parseAllowedVariables(String variablesSchemaJson) {
        if (variablesSchemaJson == null || variablesSchemaJson.isBlank() || "{}".equals(variablesSchemaJson)) {
            return Collections.emptySet();
        }
        try {
            JsonNode node = objectMapper.readTree(variablesSchemaJson);
            if (node == null || node.isNull()) {
                return Collections.emptySet();
            }
            if (node.isArray()) {
                Set<String> allowed = new HashSet<>();
                node.forEach(item -> allowed.add(item.asText()));
                return allowed;
            }
            if (node.isObject()) {
                JsonNode allowedNode = node.get("allowed");
                if (allowedNode != null && allowedNode.isArray()) {
                    Set<String> allowed = new HashSet<>();
                    allowedNode.forEach(item -> allowed.add(item.asText()));
                    return allowed;
                }
                Set<String> allowed = new HashSet<>();
                node.fieldNames().forEachRemaining(allowed::add);
                return allowed;
            }
        } catch (Exception ignored) {
        }
        return Collections.emptySet();
    }

    private void validateAllowedVariables(Set<String> placeholders, Set<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return;
        }
        for (String placeholder : placeholders) {
            if (!allowed.contains(placeholder)) {
                throw new TemplateVariableNotAllowedException("Variable not allowed: " + placeholder);
            }
        }
    }
}
