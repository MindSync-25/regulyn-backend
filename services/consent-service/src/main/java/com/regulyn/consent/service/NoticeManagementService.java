package com.regulyn.consent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.consent.entity.*;
import com.regulyn.consent.model.*;
import com.regulyn.consent.repository.*;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NoticeManagementService {
    
    private static final Logger log = LoggerFactory.getLogger(NoticeManagementService.class);
    
    private final NoticeTemplateRepository noticeTemplateRepository;
    private final NoticeVersionRepository noticeVersionRepository;
    private final NoticeLanguageTextRepository noticeLanguageTextRepository;
    private final ConsentReceiptRepository consentReceiptRepository;
    private final ConsentStatusHistoryRepository consentStatusHistoryRepository;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final String serviceName;
    
    public NoticeManagementService(
            NoticeTemplateRepository noticeTemplateRepository,
            NoticeVersionRepository noticeVersionRepository,
            NoticeLanguageTextRepository noticeLanguageTextRepository,
            ConsentReceiptRepository consentReceiptRepository,
            ConsentStatusHistoryRepository consentStatusHistoryRepository,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper,
            @Value("${spring.application.name:consent-service}") String serviceName) {
        this.noticeTemplateRepository = noticeTemplateRepository;
        this.noticeVersionRepository = noticeVersionRepository;
        this.noticeLanguageTextRepository = noticeLanguageTextRepository;
        this.consentReceiptRepository = consentReceiptRepository;
        this.consentStatusHistoryRepository = consentStatusHistoryRepository;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }
    
    @Transactional
    public CreateNoticeResponse createNotice(CreateNoticeRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        UUID userId = TenantContextHolder.getUserId();
        
        if (noticeTemplateRepository.existsByTenantIdAndPurpose(tenantId, request.purpose())) {
            throw new IllegalStateException("Notice already exists for purpose: " + request.purpose());
        }
        
        NoticeTemplate notice = new NoticeTemplate();
        notice.setTenantId(tenantId);
        notice.setPurpose(request.purpose());
        notice.setTitle(request.title());
        notice.setCategory(request.category());
        notice.setDefaultLanguage(request.defaultLanguage());
        notice.setCreatedBy(userId);
        
        notice = noticeTemplateRepository.save(notice);
        
        String noticeIdStr = notice.getNoticeId().toString();
        String purposeHash = computeSHA256(request.purpose());
        
        // Audit
        ObjectNode auditMetadata = objectMapper.createObjectNode();
        auditMetadata.put("noticeId", noticeIdStr);
        auditMetadata.put("purpose", notice.getPurpose());
        
        AuditEvent auditEvent = AuditEvent.builder()
            .tenantId(tenantId)
            .actorId(userId)
            .actorType(AuditEvent.ActorType.USER)
            .service(serviceName)
            .action("notice.created")
            .entityType("notice")
            .entityId(noticeIdStr)
            .payloadHash(purposeHash)
            .metadata(auditMetadata)
            .build();
        
        auditWriter.write(auditEvent);
        
        // Outbox
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("noticeId", noticeIdStr);
        payload.put("purpose", notice.getPurpose());
        payload.put("title", notice.getTitle());
        payload.put("category", notice.getCategory());
        
        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setEventType("notice.created");
        envelope.setTenantId(tenantId);
        envelope.setActorId(userId);
        envelope.setActorType(com.regulyn.events.model.ActorType.USER);
        envelope.setSourceService(serviceName);
        envelope.setEntityType("notice");
        envelope.setEntityId(noticeIdStr);
        envelope.setOccurredAt(Instant.now());
        envelope.setPayload(payload);
        envelope.setPayloadHash(purposeHash);
        envelope.setCorrelationId(TenantContextHolder.getRequestId());
        
        outboxWriter.write(envelope);
        
        return new CreateNoticeResponse(notice.getNoticeId());
    }
    
    @Transactional
    public CreateVersionResponse createVersion(UUID noticeId, CreateVersionRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        UUID userId = TenantContextHolder.getUserId();
        
        NoticeTemplate notice = noticeTemplateRepository.findById(noticeId)
            .orElseThrow(() -> new IllegalArgumentException("Notice not found: " + noticeId));
        
        if (!notice.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Notice not found for this tenant");
        }
        
        Integer maxVersion = noticeVersionRepository.findMaxVersionNumber(tenantId, noticeId).orElse(0);
        Integer newVersionNumber = maxVersion + 1;
        
        NoticeVersion version = new NoticeVersion();
        version.setTenantId(tenantId);
        version.setNoticeId(noticeId);
        version.setVersionNumber(newVersionNumber);
        version.setChangeSummary(request.changeSummary());
        version.setStatus("DRAFT");
        version.setCreatedBy(userId);
        
        version = noticeVersionRepository.save(version);
        
        String versionIdStr = version.getVersionId().toString();
        String versionHash = computeSHA256(noticeId.toString() + ":" + newVersionNumber);
        
        // Audit
        ObjectNode auditMetadata = objectMapper.createObjectNode();
        auditMetadata.put("noticeId", noticeId.toString());
        auditMetadata.put("versionId", versionIdStr);
        auditMetadata.put("versionNumber", newVersionNumber);
        
        AuditEvent auditEvent = AuditEvent.builder()
            .tenantId(tenantId)
            .actorId(userId)
            .actorType(AuditEvent.ActorType.USER)
            .service(serviceName)
            .action("notice.version_created")
            .entityType("notice_version")
            .entityId(versionIdStr)
            .payloadHash(versionHash)
            .metadata(auditMetadata)
            .build();
        
        auditWriter.write(auditEvent);
        
        // Outbox
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("noticeId", noticeId.toString());
        payload.put("versionId", versionIdStr);
        payload.put("versionNumber", newVersionNumber);
        payload.put("status", "DRAFT");
        
        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setEventType("notice.version_created");
        envelope.setTenantId(tenantId);
        envelope.setActorId(userId);
        envelope.setActorType(com.regulyn.events.model.ActorType.USER);
        envelope.setSourceService(serviceName);
        envelope.setEntityType("notice_version");
        envelope.setEntityId(versionIdStr);
        envelope.setOccurredAt(Instant.now());
        envelope.setPayload(payload);
        envelope.setPayloadHash(versionHash);
        envelope.setCorrelationId(TenantContextHolder.getRequestId());
        
        outboxWriter.write(envelope);
        
        return new CreateVersionResponse(version.getVersionId(), newVersionNumber);
    }
    
    @Transactional
    public AddLanguageResponse addLanguage(UUID noticeId, UUID versionId, AddLanguageRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        UUID userId = TenantContextHolder.getUserId();
        
        NoticeVersion version = noticeVersionRepository.findByTenantIdAndVersionId(tenantId, versionId)
            .orElseThrow(() -> new IllegalArgumentException("Version not found"));
        
        if (!version.getNoticeId().equals(noticeId)) {
            throw new IllegalArgumentException("Version does not belong to this notice");
        }
        
        if ("PUBLISHED".equals(version.getStatus())) {
            throw new IllegalStateException("Cannot modify published version content");
        }
        
        if (noticeLanguageTextRepository.existsByTenantIdAndVersionIdAndLanguage(tenantId, versionId, request.language())) {
            throw new IllegalStateException("Language already exists for this version: " + request.language());
        }
        
        String contentHash = computeSHA256(request.content());
        
        NoticeLanguageText languageText = new NoticeLanguageText();
        languageText.setTenantId(tenantId);
        languageText.setVersionId(versionId);
        languageText.setLanguage(request.language());
        languageText.setContent(request.content());
        languageText.setContentHash(contentHash);
        
        languageText = noticeLanguageTextRepository.save(languageText);
        
        String languageIdStr = languageText.getLanguageId().toString();
        
        // Audit
        ObjectNode auditMetadata = objectMapper.createObjectNode();
        auditMetadata.put("noticeId", noticeId.toString());
        auditMetadata.put("versionId", versionId.toString());
        auditMetadata.put("language", request.language());
        auditMetadata.put("contentHash", contentHash);
        
        AuditEvent auditEvent = AuditEvent.builder()
            .tenantId(tenantId)
            .actorId(userId)
            .actorType(AuditEvent.ActorType.USER)
            .service(serviceName)
            .action("notice.language_added")
            .entityType("notice_language")
            .entityId(languageIdStr)
            .payloadHash(contentHash)
            .metadata(auditMetadata)
            .build();
        
        auditWriter.write(auditEvent);
        
        // Outbox
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("noticeId", noticeId.toString());
        payload.put("versionId", versionId.toString());
        payload.put("language", request.language());
        payload.put("contentHash", contentHash);
        
        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setEventType("notice.language_added");
        envelope.setTenantId(tenantId);
        envelope.setActorId(userId);
        envelope.setActorType(com.regulyn.events.model.ActorType.USER);
        envelope.setSourceService(serviceName);
        envelope.setEntityType("notice_language");
        envelope.setEntityId(languageIdStr);
        envelope.setOccurredAt(Instant.now());
        envelope.setPayload(payload);
        envelope.setPayloadHash(contentHash);
        envelope.setCorrelationId(TenantContextHolder.getRequestId());
        
        outboxWriter.write(envelope);
        
        return new AddLanguageResponse(languageText.getLanguageId(), contentHash);
    }
    
    @Transactional
    public PublishVersionResponse publishVersion(UUID noticeId, UUID versionId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        UUID userId = TenantContextHolder.getUserId();
        
        NoticeVersion version = noticeVersionRepository.findByTenantIdAndVersionId(tenantId, versionId)
            .orElseThrow(() -> new IllegalArgumentException("Version not found"));
        
        if (!version.getNoticeId().equals(noticeId)) {
            throw new IllegalArgumentException("Version does not belong to this notice");
        }
        
        if ("PUBLISHED".equals(version.getStatus())) {
            throw new IllegalStateException("Version is already published");
        }
        
        // Business rule: Retire any existing PUBLISHED version for this notice
        List<NoticeVersion> publishedVersions = noticeVersionRepository
            .findByTenantIdAndNoticeIdAndStatusOrderByVersionNumberDesc(tenantId, noticeId, "PUBLISHED");
        
        for (NoticeVersion existingPublished : publishedVersions) {
            existingPublished.setStatus("RETIRED");
            noticeVersionRepository.save(existingPublished);
        }
        
        // Publish the new version
        Instant publishedAt = Instant.now();
        version.setStatus("PUBLISHED");
        version.setPublishedAt(publishedAt);
        version = noticeVersionRepository.save(version);
        
        String versionIdStr = versionId.toString();
        String publishHash = computeSHA256(noticeId.toString() + ":" + version.getVersionNumber() + ":PUBLISHED");
        
        // Audit
        ObjectNode auditMetadata = objectMapper.createObjectNode();
        auditMetadata.put("noticeId", noticeId.toString());
        auditMetadata.put("versionId", versionIdStr);
        auditMetadata.put("versionNumber", version.getVersionNumber());
        auditMetadata.put("publishedAt", publishedAt.toString());
        
        AuditEvent auditEvent = AuditEvent.builder()
            .tenantId(tenantId)
            .actorId(userId)
            .actorType(AuditEvent.ActorType.USER)
            .service(serviceName)
            .action("notice.published")
            .entityType("notice_version")
            .entityId(versionIdStr)
            .payloadHash(publishHash)
            .metadata(auditMetadata)
            .build();
        
        auditWriter.write(auditEvent);
        
        // Outbox
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("noticeId", noticeId.toString());
        payload.put("versionId", versionIdStr);
        payload.put("versionNumber", version.getVersionNumber());
        payload.put("publishedAt", publishedAt.toString());
        
        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setEventType("notice.published");
        envelope.setTenantId(tenantId);
        envelope.setActorId(userId);
        envelope.setActorType(com.regulyn.events.model.ActorType.USER);
        envelope.setSourceService(serviceName);
        envelope.setEntityType("notice_version");
        envelope.setEntityId(versionIdStr);
        envelope.setOccurredAt(Instant.now());
        envelope.setPayload(payload);
        envelope.setPayloadHash(publishHash);
        envelope.setCorrelationId(TenantContextHolder.getRequestId());
        
        outboxWriter.write(envelope);
        
        return new PublishVersionResponse(true, publishedAt);
    }
    
    @Transactional(readOnly = true)
    public ActiveNoticeResponse getActiveNotice(String purpose, String language) {
        UUID tenantId = TenantContextHolder.getTenantId();
        
        NoticeTemplate notice = noticeTemplateRepository.findByTenantIdAndPurpose(tenantId, purpose)
            .orElseThrow(() -> new IllegalArgumentException("No notice found for purpose: " + purpose));
        
        NoticeVersion publishedVersion = noticeVersionRepository
            .findByTenantIdAndNoticeIdAndStatus(tenantId, notice.getNoticeId(), "PUBLISHED")
            .orElseThrow(() -> new IllegalArgumentException("No published version found for purpose: " + purpose));
        
        // Try requested language first, fallback to default language
        NoticeLanguageText languageText = noticeLanguageTextRepository
            .findByTenantIdAndVersionIdAndLanguage(tenantId, publishedVersion.getVersionId(), language)
            .orElseGet(() -> noticeLanguageTextRepository
                .findByTenantIdAndVersionIdAndLanguage(tenantId, publishedVersion.getVersionId(), notice.getDefaultLanguage())
                .orElseThrow(() -> new IllegalStateException("No content found for published version")));
        
        return new ActiveNoticeResponse(
            notice.getNoticeId(),
            publishedVersion.getVersionId(),
            publishedVersion.getVersionNumber(),
            notice.getPurpose(),
            languageText.getLanguage(),
            languageText.getContent(),
            languageText.getContentHash(),
            publishedVersion.getPublishedAt()
        );
    }
    
    @Transactional
    public GrantConsentResponse grantConsent(GrantConsentRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        UUID userId = TenantContextHolder.getUserId();
        
        // Idempotency check
        if (request.idempotencyKey() != null) {
            Optional<ConsentReceiptEntity> existing = consentReceiptRepository
                .findByTenantIdAndDataPrincipalIdAndPurposeAndIdempotencyKey(
                    tenantId, request.dataPrincipalId(), request.purpose(), request.idempotencyKey());
            
            if (existing.isPresent()) {
                ConsentReceiptEntity receipt = existing.get();
                return new GrantConsentResponse(
                    receipt.getReceiptId(),
                    receipt.getStatus(),
                    receipt.getReceiptHash(),
                    receipt.getVersionId(),
                    receipt.getContentHash()
                );
            }
        }
        
        // Fetch active published notice
        ActiveNoticeResponse activeNotice = getActiveNotice(request.purpose(), request.language());
        
        // Compute receipt hash
        String receiptHash = computeReceiptHash(tenantId, request.dataPrincipalId(), 
            activeNotice.versionId(), activeNotice.contentHash());
        
        ConsentReceiptEntity receipt = new ConsentReceiptEntity();
        receipt.setTenantId(tenantId);
        receipt.setDataPrincipalId(request.dataPrincipalId());
        receipt.setPurpose(request.purpose());
        receipt.setSource(request.source());
        receipt.setStatus("GRANTED");
        receipt.setNoticeId(activeNotice.noticeId());
        receipt.setVersionId(activeNotice.versionId());
        receipt.setVersionNumber(activeNotice.versionNumber());
        receipt.setLanguage(activeNotice.language());
        receipt.setContentHash(activeNotice.contentHash());
        receipt.setReceiptHash(receiptHash);
        receipt.setClientRef(request.clientRef());
        receipt.setIdempotencyKey(request.idempotencyKey());
        
        receipt = consentReceiptRepository.save(receipt);
        
        String receiptIdStr = receipt.getReceiptId().toString();
        
        // Audit
        ObjectNode auditMetadata = objectMapper.createObjectNode();
        auditMetadata.put("receiptId", receiptIdStr);
        auditMetadata.put("dataPrincipalId", request.dataPrincipalId().toString());
        auditMetadata.put("purpose", request.purpose());
        auditMetadata.put("source", request.source());
        
        AuditEvent auditEvent = AuditEvent.builder()
            .tenantId(tenantId)
            .actorId(userId)
            .actorType(AuditEvent.ActorType.USER)
            .service(serviceName)
            .action("consent.granted")
            .entityType("consent")
            .entityId(receiptIdStr)
            .payloadHash(receiptHash)
            .metadata(auditMetadata)
            .build();
        
        auditWriter.write(auditEvent);
        
        // Outbox
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("receiptId", receiptIdStr);
        payload.put("dataPrincipalId", request.dataPrincipalId().toString());
        payload.put("purpose", request.purpose());
        payload.put("source", request.source());
        payload.put("versionId", activeNotice.versionId().toString());
        payload.put("contentHash", activeNotice.contentHash());
        payload.put("receiptHash", receiptHash);
        payload.put("grantedAt", receipt.getGrantedAt().toString());
        
        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setEventType("consent.granted");
        envelope.setTenantId(tenantId);
        envelope.setActorId(userId);
        envelope.setActorType(com.regulyn.events.model.ActorType.USER);
        envelope.setSourceService(serviceName);
        envelope.setEntityType("consent");
        envelope.setEntityId(receiptIdStr);
        envelope.setOccurredAt(Instant.now());
        envelope.setPayload(payload);
        envelope.setPayloadHash(receiptHash);
        envelope.setCorrelationId(TenantContextHolder.getRequestId());
        
        outboxWriter.write(envelope);
        
        return new GrantConsentResponse(
            receipt.getReceiptId(),
            "GRANTED",
            receiptHash,
            activeNotice.versionId(),
            activeNotice.contentHash()
        );
    }
    
    @Transactional
    public WithdrawConsentResponse withdrawConsent(UUID receiptId, WithdrawConsentRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        UUID userId = TenantContextHolder.getUserId();
        
        ConsentReceiptEntity receipt = consentReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("Receipt not found: " + receiptId));
        
        if (!receipt.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Receipt not found for this tenant");
        }
        
        if (!"GRANTED".equals(receipt.getStatus())) {
            throw new IllegalStateException("Can only withdraw consents with GRANTED status");
        }
        
        // Record status change in history
        ConsentStatusHistory history = new ConsentStatusHistory();
        history.setTenantId(tenantId);
        history.setReceiptId(receiptId);
        history.setFromStatus("GRANTED");
        history.setToStatus("WITHDRAWN");
        history.setChangedBy(userId);
        history.setReason(request.reason());
        consentStatusHistoryRepository.save(history);
        
        // Update receipt
        Instant withdrawnAt = Instant.now();
        receipt.setStatus("WITHDRAWN");
        receipt.setWithdrawnAt(withdrawnAt);
        receipt = consentReceiptRepository.save(receipt);
        
        String receiptIdStr = receiptId.toString();
        String withdrawHash = computeSHA256(receiptIdStr + ":WITHDRAWN:" + withdrawnAt.toString());
        
        // Audit
        ObjectNode auditMetadata = objectMapper.createObjectNode();
        auditMetadata.put("receiptId", receiptIdStr);
        auditMetadata.put("reason", request.reason() != null ? request.reason() : "");
        auditMetadata.put("withdrawnAt", withdrawnAt.toString());
        
        AuditEvent auditEvent = AuditEvent.builder()
            .tenantId(tenantId)
            .actorId(userId)
            .actorType(AuditEvent.ActorType.USER)
            .service(serviceName)
            .action("consent.withdrawn")
            .entityType("consent")
            .entityId(receiptIdStr)
            .payloadHash(withdrawHash)
            .metadata(auditMetadata)
            .build();
        
        auditWriter.write(auditEvent);
        
        // Outbox
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("receiptId", receiptIdStr);
        payload.put("dataPrincipalId", receipt.getDataPrincipalId().toString());
        payload.put("purpose", receipt.getPurpose());
        payload.put("withdrawnAt", withdrawnAt.toString());
        payload.put("reason", request.reason() != null ? request.reason() : "");
        
        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setEventType("consent.withdrawn");
        envelope.setTenantId(tenantId);
        envelope.setActorId(userId);
        envelope.setActorType(com.regulyn.events.model.ActorType.USER);
        envelope.setSourceService(serviceName);
        envelope.setEntityType("consent");
        envelope.setEntityId(receiptIdStr);
        envelope.setOccurredAt(Instant.now());
        envelope.setPayload(payload);
        envelope.setPayloadHash(withdrawHash);
        envelope.setCorrelationId(TenantContextHolder.getRequestId());
        
        outboxWriter.write(envelope);
        
        return new WithdrawConsentResponse(receiptId, "WITHDRAWN", withdrawnAt);
    }
    
    @Transactional(readOnly = true)
    public List<ConsentReceiptDto> listConsents(UUID dataPrincipalId, String purpose) {
        UUID tenantId = TenantContextHolder.getTenantId();
        
        List<ConsentReceiptEntity> receipts;
        if (purpose != null) {
            receipts = consentReceiptRepository
                .findByTenantIdAndDataPrincipalIdAndPurposeOrderByGrantedAtDesc(tenantId, dataPrincipalId, purpose);
        } else {
            receipts = consentReceiptRepository
                .findByTenantIdAndDataPrincipalIdOrderByGrantedAtDesc(tenantId, dataPrincipalId);
        }
        
        return receipts.stream()
            .map(r -> new ConsentReceiptDto(
                r.getReceiptId(),
                r.getDataPrincipalId(),
                r.getPurpose(),
                r.getSource(),
                r.getStatus(),
                r.getNoticeId(),
                r.getVersionId(),
                r.getVersionNumber(),
                r.getLanguage(),
                r.getContentHash(),
                r.getReceiptHash(),
                r.getClientRef(),
                r.getGrantedAt(),
                r.getWithdrawnAt()
            ))
            .collect(Collectors.toList());
    }
    
    private String computeSHA256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    private String computeReceiptHash(UUID tenantId, UUID dataPrincipalId, UUID versionId, String contentHash) {
        String canonical = String.format("%s|%s|%s|%s", 
            tenantId, dataPrincipalId, versionId, contentHash);
        return computeSHA256(canonical);
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
