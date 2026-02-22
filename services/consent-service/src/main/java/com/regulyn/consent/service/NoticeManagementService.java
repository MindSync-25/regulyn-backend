package com.regulyn.consent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.consent.client.TranslationClient;
import com.regulyn.consent.entity.*;
import com.regulyn.consent.model.*;
import com.regulyn.consent.repository.*;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class NoticeManagementService {
    
    private static final Logger log = LoggerFactory.getLogger(NoticeManagementService.class);
    
    private final NoticeTemplateRepository noticeTemplateRepository;
    private final NoticeVersionRepository noticeVersionRepository;
    private final NoticeLanguageTextRepository noticeLanguageTextRepository;
    private final ConsentReceiptRepository consentReceiptRepository;
    private final ConsentStatusHistoryRepository consentStatusHistoryRepository;
    private final PurposeVersionRepository purposeVersionRepository;
    private final ReconsentRequirementRepository reconsentRequirementRepository;
    private final PurposeVersionService purposeVersionService;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final TranslationClient translationClient;
    private final ObjectMapper objectMapper;
    private final String serviceName;
    
    public NoticeManagementService(
            NoticeTemplateRepository noticeTemplateRepository,
            NoticeVersionRepository noticeVersionRepository,
            NoticeLanguageTextRepository noticeLanguageTextRepository,
            ConsentReceiptRepository consentReceiptRepository,
            ConsentStatusHistoryRepository consentStatusHistoryRepository,
            PurposeVersionRepository purposeVersionRepository,
            ReconsentRequirementRepository reconsentRequirementRepository,
            PurposeVersionService purposeVersionService,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter,
            TranslationClient translationClient,
            ObjectMapper objectMapper,
            @Value("${spring.application.name:consent-service}") String serviceName) {
        this.noticeTemplateRepository = noticeTemplateRepository;
        this.noticeVersionRepository = noticeVersionRepository;
        this.noticeLanguageTextRepository = noticeLanguageTextRepository;
        this.consentReceiptRepository = consentReceiptRepository;
        this.consentStatusHistoryRepository = consentStatusHistoryRepository;
        this.purposeVersionRepository = purposeVersionRepository;
        this.reconsentRequirementRepository = reconsentRequirementRepository;
        this.purposeVersionService = purposeVersionService;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.translationClient = translationClient;
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

        NoticeTemplate notice = noticeTemplateRepository.findById(noticeId)
            .orElseThrow(() -> new IllegalArgumentException("Notice not found: " + noticeId));

        if (!notice.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Notice not found for this tenant");
        }
        
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
        return publishVersion(noticeId, versionId, null);
    }

    @Transactional
    public PublishVersionResponse publishVersion(UUID noticeId, UUID versionId, PublishVersionRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        UUID userId = TenantContextHolder.getUserId();

        NoticeTemplate notice = noticeTemplateRepository.findById(noticeId)
            .orElseThrow(() -> new IllegalArgumentException("Notice not found: " + noticeId));

        if (!notice.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Notice not found for this tenant");
        }
        
        NoticeVersion version = noticeVersionRepository.findByTenantIdAndVersionId(tenantId, versionId)
            .orElseThrow(() -> new IllegalArgumentException("Version not found"));
        
        if (!version.getNoticeId().equals(noticeId)) {
            throw new IllegalArgumentException("Version does not belong to this notice");
        }
        
        if ("PUBLISHED".equals(version.getStatus())) {
            Optional<PurposeVersion> existingPurposeVersion = purposeVersionRepository
                .findByTenantIdAndNoticeVersionIdAndPurposeKey(tenantId, versionId, notice.getPurpose());
            if (existingPurposeVersion.isEmpty() && request != null) {
                purposeVersionService.ensurePurposeVersionOnPublish(notice, version, request.purposeScope());
            }
            return new PublishVersionResponse(true, version.getPublishedAt());
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

        purposeVersionService.ensurePurposeVersionOnPublish(notice, version,
            request != null ? request.purposeScope() : null);
        
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
    public ActiveDualNoticeResponse getActiveNoticeDual(String purpose, String region) {
        if (region == null || region.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "region is required");
        }

        UUID tenantId = TenantContextHolder.getTenantId();

        NoticeTemplate notice = noticeTemplateRepository.findByTenantIdAndPurpose(tenantId, purpose)
            .orElseThrow(() -> new IllegalArgumentException("No notice found for purpose: " + purpose));

        NoticeVersion publishedVersion = noticeVersionRepository
            .findByTenantIdAndNoticeIdAndStatus(tenantId, notice.getNoticeId(), "PUBLISHED")
            .orElseThrow(() -> new IllegalArgumentException("No published version found for purpose: " + purpose));

        NoticeLanguageText englishText = noticeLanguageTextRepository
            .findByTenantIdAndVersionIdAndLanguage(tenantId, publishedVersion.getVersionId(), "en")
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "English content missing for published version"));

        String regionalLanguage = mapRegionToLanguage(region);
        NoticeLanguageText regionalText = ensureRegionalLanguageText(
            tenantId,
            notice.getNoticeId(),
            publishedVersion.getVersionId(),
            regionalLanguage,
            englishText
        );

        return new ActiveDualNoticeResponse(
            notice.getNoticeId(),
            publishedVersion.getVersionId(),
            publishedVersion.getVersionNumber(),
            notice.getPurpose(),
            region.toUpperCase(Locale.ROOT),
            new ActiveDualNoticeResponse.LanguageSnapshot(
                "en",
                englishText.getLanguageId(),
                englishText.getContent(),
                englishText.getContentHash()
            ),
            new ActiveDualNoticeResponse.LanguageSnapshot(
                regionalLanguage,
                regionalText.getLanguageId(),
                regionalText.getContent(),
                regionalText.getContentHash()
            ),
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
                    receipt.getContentHash(),
                    receipt.getPurposeVersionId()
                );
            }
        }
        
        String region = request.region();
        ActiveNoticeResponse activeNotice = null;
        ActiveDualNoticeResponse dualNotice = null;
        if (region != null && !region.isBlank()) {
            dualNotice = getActiveNoticeDual(request.purpose(), region);
        } else {
            // Fetch active published notice
            activeNotice = getActiveNotice(request.purpose(), request.language());
        }

        UUID noticeVersionId = dualNotice != null ? dualNotice.versionId() : activeNotice.versionId();

        PurposeVersion purposeVersion = purposeVersionRepository
            .findByTenantIdAndNoticeVersionIdAndPurposeKey(tenantId, noticeVersionId, request.purpose())
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,
                "Purpose version missing; publish required"));

        NoticeLanguageText languageText = null;
        if (dualNotice == null) {
            languageText = noticeLanguageTextRepository
                .findByTenantIdAndVersionIdAndLanguage(tenantId, activeNotice.versionId(), activeNotice.language())
                .orElse(null);
        }
        
        // Compute receipt hash
        String receiptHash = computeReceiptHash(tenantId, request.dataPrincipalId(),
            noticeVersionId, dualNotice != null ? dualNotice.regional().contentHash() : activeNotice.contentHash());
        
        ConsentReceiptEntity receipt = new ConsentReceiptEntity();
        receipt.setTenantId(tenantId);
        receipt.setDataPrincipalId(request.dataPrincipalId());
        receipt.setPurpose(request.purpose());
        receipt.setSource(request.source());
        receipt.setStatus("GRANTED");
        receipt.setNoticeId(dualNotice != null ? dualNotice.noticeId() : activeNotice.noticeId());
        receipt.setVersionId(noticeVersionId);
        receipt.setVersionNumber(dualNotice != null ? dualNotice.versionNumber() : activeNotice.versionNumber());
        receipt.setLanguage(dualNotice != null ? dualNotice.regional().language() : activeNotice.language());
        receipt.setContentHash(dualNotice != null ? dualNotice.regional().contentHash() : activeNotice.contentHash());
        receipt.setReceiptHash(receiptHash);
        receipt.setClientRef(request.clientRef());
        receipt.setIdempotencyKey(request.idempotencyKey());
        receipt.setPurposeVersionId(purposeVersion.getId());
        receipt.setLanguageCode(dualNotice != null ? dualNotice.regional().language() : activeNotice.language());
        receipt.setNoticeLanguageTextId(dualNotice != null ? dualNotice.regional().languageTextId() : (languageText != null ? languageText.getLanguageId() : null));
        receipt.setNoticeContentHashSha256(dualNotice != null ? dualNotice.regional().contentHash() : activeNotice.contentHash());

        if (dualNotice != null) {
            receipt.setRegionCode(dualNotice.region());
            receipt.setEnglishLanguageCode(dualNotice.english().language());
            receipt.setEnglishNoticeLanguageTextId(dualNotice.english().languageTextId());
            receipt.setEnglishContentHashSha256(dualNotice.english().contentHash());
            receipt.setRegionalNoticeLanguageTextId(dualNotice.regional().languageTextId());
            receipt.setRegionalContentHashSha256(dualNotice.regional().contentHash());
        }
        
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
        payload.put("versionId", noticeVersionId.toString());
        payload.put("contentHash", dualNotice != null ? dualNotice.regional().contentHash() : activeNotice.contentHash());
        payload.put("receiptHash", receiptHash);
        payload.put("grantedAt", receipt.getGrantedAt().toString());
        payload.put("purposeVersionId", purposeVersion.getId().toString());
        payload.put("languageCode", dualNotice != null ? dualNotice.regional().language() : activeNotice.language());
        if (dualNotice != null) {
            payload.put("noticeLanguageTextId", dualNotice.regional().languageTextId().toString());
            payload.put("regionCode", dualNotice.region());
        } else if (languageText != null) {
            payload.put("noticeLanguageTextId", languageText.getLanguageId().toString());
        }
        
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

        if (dualNotice != null) {
            ObjectNode snapshotPayload = objectMapper.createObjectNode();
            snapshotPayload.put("tenantId", tenantId.toString());
            snapshotPayload.put("receiptId", receiptIdStr);
            snapshotPayload.put("noticeId", receipt.getNoticeId().toString());
            snapshotPayload.put("versionId", receipt.getVersionId().toString());
            snapshotPayload.put("regionCode", dualNotice.region());
            snapshotPayload.put("englishLanguage", dualNotice.english().language());
            snapshotPayload.put("englishLanguageTextId", dualNotice.english().languageTextId().toString());
            snapshotPayload.put("englishContentHash", dualNotice.english().contentHash());
            snapshotPayload.put("regionalLanguage", dualNotice.regional().language());
            snapshotPayload.put("regionalLanguageTextId", dualNotice.regional().languageTextId().toString());
            snapshotPayload.put("regionalContentHash", dualNotice.regional().contentHash());

            String snapshotHash = computeSHA256(receiptIdStr + ":CONSENT_LANGUAGE_SNAPSHOT_STORED");

            AuditEvent snapshotAudit = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(userId)
                .actorType(AuditEvent.ActorType.USER)
                .service(serviceName)
                .action("CONSENT_LANGUAGE_SNAPSHOT_STORED")
                .entityType("consent_receipt")
                .entityId(receiptIdStr)
                .payloadHash(snapshotHash)
                .metadata(snapshotPayload)
                .build();
            auditWriter.write(snapshotAudit);

            EventEnvelopeV1 snapshotEnvelope = new EventEnvelopeV1();
            snapshotEnvelope.setEventId(UUID.randomUUID());
            snapshotEnvelope.setEventType("CONSENT_LANGUAGE_SNAPSHOT_STORED");
            snapshotEnvelope.setTenantId(tenantId);
            snapshotEnvelope.setActorId(userId);
            snapshotEnvelope.setActorType(com.regulyn.events.model.ActorType.USER);
            snapshotEnvelope.setSourceService(serviceName);
            snapshotEnvelope.setEntityType("consent_receipt");
            snapshotEnvelope.setEntityId(receiptIdStr);
            snapshotEnvelope.setOccurredAt(Instant.now());
            snapshotEnvelope.setPayload(snapshotPayload);
            snapshotEnvelope.setPayloadHash(snapshotHash);
            snapshotEnvelope.setCorrelationId(TenantContextHolder.getRequestId());
            outboxWriter.write(snapshotEnvelope);
        }
        
        Optional<ReconsentRequirement> requirement = reconsentRequirementRepository
            .findByTenantIdAndDataPrincipalIdAndRequiredPurposeVersionId(
                tenantId, request.dataPrincipalId(), purposeVersion.getId());
        if (requirement.isPresent() && requirement.get().getStatus() == ReconsentStatus.REQUIRED) {
            ReconsentRequirement existing = requirement.get();
            existing.setStatus(ReconsentStatus.SATISFIED);
            existing.setSatisfiedAt(Instant.now());
            existing.setSatisfiedByConsentReceiptId(receipt.getReceiptId());
            reconsentRequirementRepository.save(existing);

            ObjectNode satisfiedPayload = objectMapper.createObjectNode();
            satisfiedPayload.put("tenantId", tenantId.toString());
            satisfiedPayload.put("dataPrincipalId", request.dataPrincipalId().toString());
            satisfiedPayload.put("requiredPurposeVersionId", purposeVersion.getId().toString());
            satisfiedPayload.put("consentReceiptId", receipt.getReceiptId().toString());
            satisfiedPayload.put("satisfiedAt", existing.getSatisfiedAt().toString());

            String satisfiedHash = computeSHA256(receipt.getReceiptId().toString() + ":RECONSENT_SATISFIED");
            AuditEvent satisfiedAudit = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(userId)
                .actorType(AuditEvent.ActorType.USER)
                .service(serviceName)
                .action("RECONSENT_SATISFIED")
                .entityType("reconsent_requirement")
                .entityId(existing.getId().toString())
                .payloadHash(satisfiedHash)
                .metadata(satisfiedPayload)
                .build();
            auditWriter.write(satisfiedAudit);

            EventEnvelopeV1 satisfiedEnvelope = new EventEnvelopeV1();
            satisfiedEnvelope.setEventId(UUID.randomUUID());
            satisfiedEnvelope.setEventType("RECONSENT_SATISFIED");
            satisfiedEnvelope.setTenantId(tenantId);
            satisfiedEnvelope.setActorId(userId);
            satisfiedEnvelope.setActorType(com.regulyn.events.model.ActorType.USER);
            satisfiedEnvelope.setSourceService(serviceName);
            satisfiedEnvelope.setEntityType("reconsent_requirement");
            satisfiedEnvelope.setEntityId(existing.getId().toString());
            satisfiedEnvelope.setOccurredAt(Instant.now());
            satisfiedEnvelope.setPayload(satisfiedPayload);
            satisfiedEnvelope.setPayloadHash(satisfiedHash);
            satisfiedEnvelope.setCorrelationId(TenantContextHolder.getRequestId());
            outboxWriter.write(satisfiedEnvelope);
        }

        return new GrantConsentResponse(
            receipt.getReceiptId(),
            "GRANTED",
            receiptHash,
            noticeVersionId,
            dualNotice != null ? dualNotice.regional().contentHash() : activeNotice.contentHash(),
            purposeVersion.getId()
        );
    }

    private String mapRegionToLanguage(String region) {
        String normalized = region.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "KA" -> "kn";
            case "TN" -> "ta";
            case "TS", "AP" -> "te";
            case "KL" -> "ml";
            case "MH" -> "mr";
            case "WB" -> "bn";
            case "GJ" -> "gu";
            case "PB" -> "pa";
            case "OD", "OR" -> "or";
            case "AS" -> "as";
            default -> "hi";
        };
    }

    private NoticeLanguageText ensureRegionalLanguageText(UUID tenantId,
                                                         UUID noticeId,
                                                         UUID versionId,
                                                         String regionalLanguage,
                                                         NoticeLanguageText englishText) {
        Optional<NoticeLanguageText> existing = noticeLanguageTextRepository
            .findByTenantIdAndVersionIdAndLanguage(tenantId, versionId, regionalLanguage);
        if (existing.isPresent()) {
            return existing.get();
        }

        String translated = translationClient.translate(englishText.getContent(), "en", regionalLanguage);
        if (translated == null || translated.isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Regional translation unavailable");
        }

        String translationEngine = translationClient.getEngine();
        String translationEngineVersion = translationClient.getEngineVersion();

        String contentHash = computeSHA256(translated);
        NoticeLanguageText languageText = new NoticeLanguageText();
        languageText.setTenantId(tenantId);
        languageText.setVersionId(versionId);
        languageText.setLanguage(regionalLanguage);
        languageText.setContent(translated);
        languageText.setContentHash(contentHash);
        languageText.setTranslationSource("AUTO");
        languageText.setTranslationEngine(translationEngine);
        languageText.setTranslationEngineVersion(translationEngineVersion);
        languageText.setTranslatedFromLanguage("en");
        languageText.setTranslatedAt(Instant.now());

        try {
            languageText = noticeLanguageTextRepository.save(languageText);
        } catch (DataIntegrityViolationException ex) {
            Optional<NoticeLanguageText> deduped = noticeLanguageTextRepository
                .findByTenantIdAndVersionIdAndLanguage(tenantId, versionId, regionalLanguage);
            if (deduped.isPresent()) {
                return deduped.get();
            }
            throw ex;
        }

        ObjectNode auditMetadata = objectMapper.createObjectNode();
        auditMetadata.put("tenantId", tenantId.toString());
        auditMetadata.put("noticeId", noticeId.toString());
        auditMetadata.put("versionId", versionId.toString());
        auditMetadata.put("language", regionalLanguage);
        auditMetadata.put("languageTextId", languageText.getLanguageId().toString());
        auditMetadata.put("contentHash", contentHash);
        auditMetadata.put("translationSource", "AUTO");
        if (translationEngine != null && !translationEngine.isBlank()) {
            auditMetadata.put("translationEngine", translationEngine);
        }
        if (translationEngineVersion != null && !translationEngineVersion.isBlank()) {
            auditMetadata.put("translationEngineVersion", translationEngineVersion);
        }
        auditMetadata.put("translatedAt", languageText.getTranslatedAt().toString());

        String payloadHash = computeSHA256(languageText.getLanguageId().toString() + ":CONSENT_LANGUAGE_TRANSLATION_GENERATED");

        AuditEvent auditEvent = AuditEvent.builder()
            .tenantId(tenantId)
            .actorId(TenantContextHolder.getUserId())
            .actorType(AuditEvent.ActorType.USER)
            .service(serviceName)
            .action("CONSENT_LANGUAGE_TRANSLATION_GENERATED")
            .entityType("notice_language")
            .entityId(languageText.getLanguageId().toString())
            .payloadHash(payloadHash)
            .metadata(auditMetadata)
            .build();
        auditWriter.write(auditEvent);

        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setEventType("CONSENT_LANGUAGE_TRANSLATION_GENERATED");
        envelope.setTenantId(tenantId);
        envelope.setActorId(TenantContextHolder.getUserId());
        envelope.setActorType(com.regulyn.events.model.ActorType.USER);
        envelope.setSourceService(serviceName);
        envelope.setEntityType("notice_language");
        envelope.setEntityId(languageText.getLanguageId().toString());
        envelope.setOccurredAt(Instant.now());
        envelope.setPayload(auditMetadata);
        envelope.setPayloadHash(payloadHash);
        envelope.setCorrelationId(TenantContextHolder.getRequestId());
        outboxWriter.write(envelope);

        return languageText;
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
    
    @Transactional(readOnly = true)
    public List<NoticeListItemDto> listNotices() {
        UUID tenantId = TenantContextHolder.getTenantId();
        List<NoticeTemplate> templates = noticeTemplateRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        return templates.stream()
            .map(t -> {
                Optional<NoticeVersion> latestVersion = noticeVersionRepository
                    .findTopByTenantIdAndNoticeIdOrderByVersionNumberDesc(tenantId, t.getNoticeId());
                return new NoticeListItemDto(
                    t.getNoticeId(),
                    t.getPurpose(),
                    t.getTitle(),
                    t.getCategory(),
                    t.getDefaultLanguage(),
                    t.getCreatedAt(),
                    latestVersion.map(NoticeVersion::getStatus).orElse(null),
                    latestVersion.map(NoticeVersion::getVersionId).orElse(null),
                    latestVersion.map(NoticeVersion::getVersionNumber).orElse(null)
                );
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PurposeVersionListItemDto> listPurposeVersions() {
        UUID tenantId = TenantContextHolder.getTenantId();
        return purposeVersionRepository
            .findByTenantIdOrderByPurposeKeyAscVersionNumDesc(tenantId)
            .stream()
            .map(pv -> {
                String legalBasis = null;
                Integer retentionDays = null;
                List<String> dataCategories = List.of();
                List<String> dataFields = List.of();
                List<String> processingActivities = List.of();
                try {
                    com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(pv.getScopeJson());
                    if (node.has("legalBasis") && !node.get("legalBasis").isNull()) {
                        legalBasis = node.get("legalBasis").asText();
                    }
                    if (node.has("retentionDays") && !node.get("retentionDays").isNull()) {
                        retentionDays = node.get("retentionDays").asInt();
                    }
                    if (node.has("dataCategories") && node.get("dataCategories").isArray()) {
                        dataCategories = objectMapper.convertValue(node.get("dataCategories"),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    }
                    if (node.has("dataFields") && node.get("dataFields").isArray()) {
                        dataFields = objectMapper.convertValue(node.get("dataFields"),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    }
                    if (node.has("processingActivities") && node.get("processingActivities").isArray()) {
                        processingActivities = objectMapper.convertValue(node.get("processingActivities"),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    }
                } catch (Exception ignored) {}
                return new PurposeVersionListItemDto(
                    pv.getId(),
                    pv.getPurposeKey(),
                    pv.getVersionNum(),
                    pv.getScopeHashSha256(),
                    legalBasis,
                    retentionDays,
                    dataCategories,
                    dataFields,
                    processingActivities,
                    pv.getNoticeId(),
                    pv.getNoticeVersionId(),
                    pv.getCreatedAt()
                );
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReconsentRequirementListItemDto> listReconsentRequirements() {
        UUID tenantId = TenantContextHolder.getTenantId();
        return reconsentRequirementRepository
            .findByTenantIdOrderByCreatedAtDesc(tenantId)
            .stream()
            .map(r -> new ReconsentRequirementListItemDto(
                r.getId(),
                r.getDataPrincipalId(),
                r.getPurposeKey(),
                r.getNoticeId(),
                r.getRequiredPurposeVersionId(),
                r.getStatus() != null ? r.getStatus().name() : null,
                r.getCreatedAt(),
                r.getSatisfiedAt(),
                r.getSatisfiedByConsentReceiptId()
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
