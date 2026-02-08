package com.regulyn.consent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.consent.client.EvidenceClient;
import com.regulyn.consent.entity.*;
import com.regulyn.consent.model.PurposeScopeDto;
import com.regulyn.consent.repository.*;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
public class PurposeVersionService {

    private static final Logger log = LoggerFactory.getLogger(PurposeVersionService.class);

    private final PurposeVersionRepository purposeVersionRepository;
    private final PurposeVersionHistoryRepository purposeVersionHistoryRepository;
    private final ConsentReceiptRepository consentReceiptRepository;
    private final ConsentInvalidationRepository consentInvalidationRepository;
    private final ReconsentRequirementRepository reconsentRequirementRepository;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final EvidenceClient evidenceClient;
    private final String serviceName;

    public PurposeVersionService(PurposeVersionRepository purposeVersionRepository,
                                 PurposeVersionHistoryRepository purposeVersionHistoryRepository,
                                 ConsentReceiptRepository consentReceiptRepository,
                                 ConsentInvalidationRepository consentInvalidationRepository,
                                 ReconsentRequirementRepository reconsentRequirementRepository,
                                 AuditWriter auditWriter,
                                 OutboxWriter outboxWriter,
                                 ObjectMapper objectMapper,
                                 EvidenceClient evidenceClient,
                                 @Value("${spring.application.name:consent-service}") String serviceName) {
        this.purposeVersionRepository = purposeVersionRepository;
        this.purposeVersionHistoryRepository = purposeVersionHistoryRepository;
        this.consentReceiptRepository = consentReceiptRepository;
        this.consentInvalidationRepository = consentInvalidationRepository;
        this.reconsentRequirementRepository = reconsentRequirementRepository;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.evidenceClient = evidenceClient;
        this.serviceName = serviceName;
    }

    @Transactional
    public PurposeVersion ensurePurposeVersionOnPublish(NoticeTemplate notice,
                                                       NoticeVersion publishedVersion,
                                                       PurposeScopeDto purposeScopeDto) {
        UUID tenantId = notice.getTenantId();
        UUID noticeId = notice.getNoticeId();
        UUID noticeVersionId = publishedVersion.getVersionId();
        String purposeKey = notice.getPurpose();
        UUID actorId = TenantContextHolder.getUserId();

        Optional<PurposeVersion> existing = purposeVersionRepository
            .findByTenantIdAndNoticeVersionIdAndPurposeKey(tenantId, noticeVersionId, purposeKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        PurposeVersion prior = purposeVersionRepository
            .findTopByTenantIdAndNoticeIdAndPurposeKeyOrderByVersionNumDesc(tenantId, noticeId, purposeKey)
            .orElse(null);

        PurposeScope currentScope = PurposeScope.fromDto(purposeScopeDto);
        PurposeScope previousScope = prior != null ? PurposeScope.fromJson(objectMapper, prior.getScopeJson()) : null;

        PurposeScopeComparator comparator = new PurposeScopeComparator();
        PurposeScopeDiffResult diff = comparator.compare(previousScope, currentScope);

        String scopeJson = currentScope.toCanonicalJson(objectMapper);
        String scopeHash = sha256(scopeJson);

        PurposeVersion purposeVersion = new PurposeVersion();
        purposeVersion.setTenantId(tenantId);
        purposeVersion.setNoticeId(noticeId);
        purposeVersion.setNoticeVersionId(noticeVersionId);
        purposeVersion.setPurposeKey(purposeKey);
        purposeVersion.setVersionNum(prior != null ? prior.getVersionNum() + 1 : 1);
        purposeVersion.setScopeJson(scopeJson);
        purposeVersion.setScopeHashSha256(scopeHash);
        purposeVersion.setCreatedByActorId(actorId);
        purposeVersion.setCreatedByActorType("USER");

        purposeVersion = purposeVersionRepository.save(purposeVersion);

        PurposeVersionHistory history = new PurposeVersionHistory();
        history.setTenantId(tenantId);
        history.setNoticeId(noticeId);
        history.setPurposeKey(purposeKey);
        history.setFromPurposeVersionId(prior != null ? prior.getId() : null);
        history.setToPurposeVersionId(purposeVersion.getId());
        history.setChangeSummary(diff.changed() ? String.join(",", diff.reasons()) : null);
        purposeVersionHistoryRepository.save(history);

        Map<String, Object> evidencePayload = new LinkedHashMap<>();
        evidencePayload.put("noticeId", noticeId.toString());
        evidencePayload.put("purposeKey", purposeKey);
        evidencePayload.put("purposeVersionId", purposeVersion.getId().toString());
        evidencePayload.put("scopeHash", scopeHash);

        UUID evidenceId = evidenceClient.createArtifact(
            tenantId,
            "PURPOSE_VERSION_CREATED",
            evidencePayload
        );

        Map<String, Object> createdPayload = new LinkedHashMap<>();
        createdPayload.put("tenantId", tenantId.toString());
        createdPayload.put("noticeId", noticeId.toString());
        createdPayload.put("noticeVersionId", noticeVersionId.toString());
        createdPayload.put("purposeKey", purposeKey);
        createdPayload.put("purposeVersionId", purposeVersion.getId().toString());
        createdPayload.put("versionNum", purposeVersion.getVersionNum());
        createdPayload.put("scopeHash", scopeHash);
        createdPayload.put("evidenceArtifactId", evidenceId != null ? evidenceId.toString() : null);

        writeAuditAndOutbox(
            "PURPOSE_VERSION_CREATED",
            "purpose_version",
            purposeVersion.getId().toString(),
            actorId,
            buildPayload(createdPayload)
        );

        if (diff.widened()) {
            Map<String, Object> widenedPayload = new LinkedHashMap<>();
            widenedPayload.put("tenantId", tenantId.toString());
            widenedPayload.put("noticeId", noticeId.toString());
            widenedPayload.put("purposeKey", purposeKey);
            widenedPayload.put("fromPurposeVersionId", prior != null ? prior.getId().toString() : null);
            widenedPayload.put("toPurposeVersionId", purposeVersion.getId().toString());
            widenedPayload.put("reasonCodes", diff.reasons());
            widenedPayload.put("detectedAt", Instant.now().toString());
            widenedPayload.put("evidenceArtifactId", evidenceId != null ? evidenceId.toString() : null);

            writeAuditAndOutbox(
                "PURPOSE_WIDENED_DETECTED",
                "purpose_version",
                purposeVersion.getId().toString(),
                actorId,
                buildPayload(widenedPayload)
            );

            invalidateConsents(tenantId, noticeId, purposeKey, prior, purposeVersion, actorId);
        }

        return purposeVersion;
    }

    private void invalidateConsents(UUID tenantId,
                                    UUID noticeId,
                                    String purposeKey,
                                    PurposeVersion prior,
                                    PurposeVersion current,
                                    UUID actorId) {
        UUID priorId = prior != null ? prior.getId() : null;
        List<ConsentReceiptEntity> receipts = consentReceiptRepository
            .findGrantedForPurposeAndPriorOrLegacy(tenantId, purposeKey, priorId);

        for (ConsentReceiptEntity receipt : receipts) {
            boolean createdInvalidation = false;
            if (!consentInvalidationRepository.existsByTenantIdAndConsentReceiptId(tenantId, receipt.getReceiptId())) {
                ConsentInvalidation invalidation = new ConsentInvalidation();
                invalidation.setTenantId(tenantId);
                invalidation.setDataPrincipalId(receipt.getDataPrincipalId());
                invalidation.setConsentReceiptId(receipt.getReceiptId());
                invalidation.setInvalidationReason("PURPOSE_WIDENED");
                invalidation.setTriggeredByPurposeVersionId(current.getId());
                try {
                    consentInvalidationRepository.save(invalidation);
                    createdInvalidation = true;
                } catch (DataIntegrityViolationException ex) {
                    createdInvalidation = false;
                }
            }

            if (createdInvalidation) {
                Map<String, Object> invalidationPayload = new LinkedHashMap<>();
                invalidationPayload.put("tenantId", tenantId.toString());
                invalidationPayload.put("dataPrincipalId", receipt.getDataPrincipalId().toString());
                invalidationPayload.put("consentReceiptId", receipt.getReceiptId().toString());
                invalidationPayload.put("noticeId", noticeId.toString());
                invalidationPayload.put("purposeKey", purposeKey);
                invalidationPayload.put("fromPurposeVersionId", prior != null ? prior.getId().toString() : null);
                invalidationPayload.put("toPurposeVersionId", current.getId().toString());

                writeAuditAndOutbox(
                    "CONSENT_INVALIDATED_DUE_TO_PURPOSE_CHANGE",
                    "consent_invalidation",
                    receipt.getReceiptId().toString(),
                    actorId,
                    buildPayload(invalidationPayload)
                );
            }

            boolean createdRequirement = false;
            Optional<ReconsentRequirement> existing = reconsentRequirementRepository
                .findByTenantIdAndDataPrincipalIdAndRequiredPurposeVersionId(
                    tenantId, receipt.getDataPrincipalId(), current.getId());
            if (existing.isEmpty()) {
                ReconsentRequirement requirement = new ReconsentRequirement();
                requirement.setTenantId(tenantId);
                requirement.setDataPrincipalId(receipt.getDataPrincipalId());
                requirement.setNoticeId(noticeId);
                requirement.setPurposeKey(purposeKey);
                requirement.setRequiredPurposeVersionId(current.getId());
                requirement.setStatus(ReconsentStatus.REQUIRED);
                try {
                    reconsentRequirementRepository.save(requirement);
                    createdRequirement = true;
                } catch (DataIntegrityViolationException ex) {
                    createdRequirement = false;
                }
            }

            if (createdRequirement) {
                writeAuditAndOutbox(
                    "RECONSENT_REQUIRED",
                    "reconsent_requirement",
                    current.getId().toString(),
                    actorId,
                    buildPayload(Map.of(
                        "tenantId", tenantId.toString(),
                        "dataPrincipalId", receipt.getDataPrincipalId().toString(),
                        "noticeId", noticeId.toString(),
                        "purposeKey", purposeKey,
                        "requiredPurposeVersionId", current.getId().toString()
                    ))
                );
            }
        }
    }

    private void writeAuditAndOutbox(String eventType,
                                    String entityType,
                                    String entityId,
                                    UUID actorId,
                                    ObjectNode payload) {
        String payloadHash = sha256(payload.toString());

        AuditEvent auditEvent = AuditEvent.builder()
            .tenantId(TenantContextHolder.getTenantId())
            .actorId(actorId)
            .actorType(AuditEvent.ActorType.USER)
            .service(serviceName)
            .action(eventType)
            .entityType(entityType)
            .entityId(entityId)
            .payloadHash(payloadHash)
            .metadata(payload)
            .build();

        auditWriter.write(auditEvent);

        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setEventType(eventType);
        envelope.setTenantId(TenantContextHolder.getTenantId());
        envelope.setActorId(actorId);
        envelope.setActorType(com.regulyn.events.model.ActorType.USER);
        envelope.setSourceService(serviceName);
        envelope.setEntityType(entityType);
        envelope.setEntityId(entityId);
        envelope.setOccurredAt(Instant.now());
        envelope.setPayload(payload);
        envelope.setPayloadHash(payloadHash);
        envelope.setCorrelationId(TenantContextHolder.getRequestId());

        outboxWriter.write(envelope);
    }

    private ObjectNode buildPayload(Map<String, Object> values) {
        ObjectNode node = objectMapper.createObjectNode();
        values.forEach((key, value) -> {
            if (value == null) {
                node.putNull(key);
            } else if (value instanceof String str) {
                node.put(key, str);
            } else if (value instanceof Integer integer) {
                node.put(key, integer);
            } else if (value instanceof Boolean bool) {
                node.put(key, bool);
            } else if (value instanceof List<?> list) {
                node.putPOJO(key, list);
            } else {
                node.putPOJO(key, value);
            }
        });
        return node;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
