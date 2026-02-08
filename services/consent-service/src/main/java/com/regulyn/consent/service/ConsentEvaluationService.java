package com.regulyn.consent.service;

import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.consent.entity.ConsentReceiptEntity;
import com.regulyn.consent.entity.PurposeVersion;
import com.regulyn.consent.entity.ReconsentRequirement;
import com.regulyn.consent.entity.ReconsentStatus;
import com.regulyn.consent.model.ActivePurposeVersionResponse;
import com.regulyn.consent.model.ConsentValidityResponse;
import com.regulyn.consent.repository.ConsentInvalidationRepository;
import com.regulyn.consent.repository.ConsentReceiptRepository;
import com.regulyn.consent.repository.NoticeVersionRepository;
import com.regulyn.consent.repository.PurposeVersionRepository;
import com.regulyn.consent.entity.NoticeVersion;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class ConsentEvaluationService {

    private final PurposeVersionRepository purposeVersionRepository;
    private final NoticeVersionRepository noticeVersionRepository;
    private final ConsentReceiptRepository consentReceiptRepository;
    private final ConsentInvalidationRepository consentInvalidationRepository;
    private final com.regulyn.consent.repository.ReconsentRequirementRepository reconsentRequirementRepository;

    public ConsentEvaluationService(PurposeVersionRepository purposeVersionRepository,
                                    NoticeVersionRepository noticeVersionRepository,
                                    ConsentReceiptRepository consentReceiptRepository,
                                    ConsentInvalidationRepository consentInvalidationRepository,
                                    com.regulyn.consent.repository.ReconsentRequirementRepository reconsentRequirementRepository) {
        this.purposeVersionRepository = purposeVersionRepository;
        this.noticeVersionRepository = noticeVersionRepository;
        this.consentReceiptRepository = consentReceiptRepository;
        this.consentInvalidationRepository = consentInvalidationRepository;
        this.reconsentRequirementRepository = reconsentRequirementRepository;
    }

    public ConsentValidityResponse validateConsent(UUID dataPrincipalId, String purpose, UUID requiredPurposeVersionId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        Optional<PurposeVersion> requiredVersion = purposeVersionRepository.findById(requiredPurposeVersionId);
        if (requiredVersion.isEmpty() || !tenantId.equals(requiredVersion.get().getTenantId())) {
            return new ConsentValidityResponse(tenantId, dataPrincipalId, purpose, requiredPurposeVersionId,
                false, "UNKNOWN_FAIL_CLOSED", "UNKNOWN");
        }

        Optional<ReconsentRequirement> requirement = reconsentRequirementRepository
            .findByTenantIdAndDataPrincipalIdAndRequiredPurposeVersionId(tenantId, dataPrincipalId, requiredPurposeVersionId);

        if (requirement.isPresent() && requirement.get().getStatus() == ReconsentStatus.REQUIRED) {
            return new ConsentValidityResponse(tenantId, dataPrincipalId, purpose, requiredPurposeVersionId,
                false, "RECONSENT_REQUIRED", "REQUIRED");
        }

        Optional<ConsentReceiptEntity> receipt = consentReceiptRepository
            .findTopByTenantIdAndDataPrincipalIdAndPurposeAndStatusAndPurposeVersionIdOrderByGrantedAtDesc(
                tenantId, dataPrincipalId, purpose, "GRANTED", requiredPurposeVersionId);

        if (receipt.isEmpty()) {
            return new ConsentValidityResponse(tenantId, dataPrincipalId, purpose, requiredPurposeVersionId,
                false, "NO_GRANT_FOUND", "NONE");
        }

        ConsentReceiptEntity entity = receipt.get();
        if (entity.getRegionCode() != null && !entity.getRegionCode().isBlank()) {
            boolean englishMissing = entity.getEnglishContentHashSha256() == null || entity.getEnglishNoticeLanguageTextId() == null;
            boolean regionalMissing = entity.getRegionalContentHashSha256() == null || entity.getRegionalNoticeLanguageTextId() == null;
            if (englishMissing || regionalMissing) {
                return new ConsentValidityResponse(tenantId, dataPrincipalId, purpose, requiredPurposeVersionId,
                    false, "LANGUAGE_SNAPSHOT_MISSING", entity.getStatus());
            }
        }
        boolean invalidated = consentInvalidationRepository.existsByTenantIdAndConsentReceiptId(tenantId, entity.getReceiptId());
        if (invalidated) {
            return new ConsentValidityResponse(tenantId, dataPrincipalId, purpose, requiredPurposeVersionId,
                false, "INVALIDATED", entity.getStatus());
        }

        if (!"GRANTED".equals(entity.getStatus())) {
            return new ConsentValidityResponse(tenantId, dataPrincipalId, purpose, requiredPurposeVersionId,
                false, "WITHDRAWN", entity.getStatus());
        }

        return new ConsentValidityResponse(tenantId, dataPrincipalId, purpose, requiredPurposeVersionId,
            true, "OK", entity.getStatus());
    }

    public ActivePurposeVersionResponse getActivePurposeVersion(UUID noticeId, String purposeKey) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Optional<NoticeVersion> published = noticeVersionRepository.findByTenantIdAndNoticeIdAndStatus(tenantId, noticeId, "PUBLISHED");
        if (published.isEmpty()) {
            return null;
        }
        Optional<PurposeVersion> purposeVersion = purposeVersionRepository
            .findByTenantIdAndNoticeVersionIdAndPurposeKey(tenantId, published.get().getVersionId(), purposeKey);
        if (purposeVersion.isEmpty()) {
            return null;
        }
        PurposeVersion pv = purposeVersion.get();
        return new ActivePurposeVersionResponse(
            pv.getId(),
            pv.getVersionNum(),
            pv.getScopeHashSha256(),
            pv.getNoticeVersionId(),
            published.get().getPublishedAt()
        );
    }
}
