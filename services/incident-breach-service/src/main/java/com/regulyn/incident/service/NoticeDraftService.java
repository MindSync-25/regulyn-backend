package com.regulyn.incident.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.incident.entity.NoticeDraftEntity;
import com.regulyn.incident.entity.NoticeTemplateVersionEntity;
import com.regulyn.incident.events.AuditOutboxWriter;
import com.regulyn.incident.exception.TemplateVariableNotAllowedException;
import com.regulyn.incident.exception.VersionNotFoundException;
import com.regulyn.incident.repository.NoticeDraftRepository;
import com.regulyn.incident.repository.NoticeTemplateVersionRepository;
import com.regulyn.incident.util.HashingUtil;
import com.regulyn.incident.util.TemplateRenderer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Service
public class NoticeDraftService {

    private final NoticeDraftRepository draftRepository;
    private final NoticeTemplateVersionRepository versionRepository;
    private final AuditOutboxWriter auditOutboxWriter;
    private final ObjectMapper objectMapper;

    public NoticeDraftService(
            NoticeDraftRepository draftRepository,
            NoticeTemplateVersionRepository versionRepository,
            AuditOutboxWriter auditOutboxWriter,
            ObjectMapper objectMapper) {
        this.draftRepository = draftRepository;
        this.versionRepository = versionRepository;
        this.auditOutboxWriter = auditOutboxWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NoticeDraftEntity createDraftForIncident(UUID tenantId,
                                                    UUID incidentId,
                                                    UUID templateVersionId,
                                                    String noticeType,
                                                    String language,
                                                    Map<String, String> variables,
                                                    UUID actorId) {
        NoticeTemplateVersionEntity version = versionRepository.findById(templateVersionId)
                .orElseThrow(() -> new VersionNotFoundException("Template version not found"));

        String resolvedLanguage = language != null ? language : version.getLanguage();
        if (language != null && !language.equals(version.getLanguage())) {
            throw new IllegalArgumentException("Draft language must match template version language");
        }

        Set<String> placeholders = TemplateRenderer.extractPlaceholders(version.getContent());
        validateAllowedVariables(placeholders, parseAllowedVariables(version.getVariablesSchema()));

        String rendered = TemplateRenderer.render(version.getContent(), variables);
        String renderedHash = HashingUtil.sha256Hex(rendered);

        NoticeDraftEntity draft = new NoticeDraftEntity();
        draft.setTenantId(tenantId);
        draft.setIncidentId(incidentId);
        draft.setNoticeType(noticeType);
        draft.setTemplateVersionId(templateVersionId);
        draft.setLanguage(resolvedLanguage);
        draft.setRenderedContent(rendered);
        draft.setRenderedSha256(renderedHash);
        draft.setStatus("DRAFT");
        draft.setCreatedBy(actorId != null ? actorId.toString() : "system");
        draft.setCreatedAt(Instant.now());
        draft.setUpdatedAt(Instant.now());

        try {
            NoticeDraftEntity saved = draftRepository.save(draft);
            publishDraftCreatedEvent(tenantId, incidentId, version, saved, actorId);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueViolation(ex)) {
                return draftRepository
                        .findByIncidentIdAndTemplateVersionIdAndNoticeType(incidentId, templateVersionId, noticeType)
                        .orElseThrow(() -> ex);
            }
            throw ex;
        }
    }

    private void publishDraftCreatedEvent(UUID tenantId,
                                          UUID incidentId,
                                          NoticeTemplateVersionEntity version,
                                          NoticeDraftEntity draft,
                                          UUID actorId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenant_id", tenantId.toString());
        payload.put("template_id", version.getTemplateId().toString());
        payload.put("version_id", version.getId().toString());
        payload.put("draft_id", draft.getId().toString());
        payload.put("incident_id", incidentId.toString());
        payload.put("template_type", draft.getNoticeType());
        payload.put("language", draft.getLanguage());
        payload.put("version", version.getVersion());
        payload.put("rendered_sha256", draft.getRenderedSha256());
        payload.put("created_by", draft.getCreatedBy());
        payload.put("created_at", draft.getCreatedAt().toString());

        auditOutboxWriter.publish(
                "NOTICE_DRAFT_CREATED",
                "notice_draft",
                draft.getId().toString(),
                tenantId,
                actorId,
                payload
        );
    }

    private boolean isUniqueViolation(DataIntegrityViolationException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return "23505".equals(sqlException.getSQLState());
            }
            current = current.getCause();
        }
        return false;
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
