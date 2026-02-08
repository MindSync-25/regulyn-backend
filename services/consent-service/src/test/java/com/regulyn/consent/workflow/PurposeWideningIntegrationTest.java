package com.regulyn.consent.workflow;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.consent.ConsentServiceApplication;
import com.regulyn.consent.entity.ConsentReceiptEntity;
import com.regulyn.consent.entity.PurposeVersion;
import com.regulyn.consent.entity.ReconsentRequirement;
import com.regulyn.consent.model.*;
import com.regulyn.consent.repository.ConsentInvalidationRepository;
import com.regulyn.consent.repository.ConsentReceiptRepository;
import com.regulyn.consent.repository.PurposeVersionRepository;
import com.regulyn.consent.repository.ReconsentRequirementRepository;
import com.regulyn.consent.service.ConsentEvaluationService;
import com.regulyn.consent.service.NoticeManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ConsentServiceApplication.class)
@Testcontainers
public class PurposeWideningIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("consent")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private NoticeManagementService noticeManagementService;

    @Autowired
    private ConsentEvaluationService consentEvaluationService;

    @Autowired
    private PurposeVersionRepository purposeVersionRepository;

    @Autowired
    private ConsentInvalidationRepository consentInvalidationRepository;

    @Autowired
    private ReconsentRequirementRepository reconsentRequirementRepository;

    @Autowired
    private ConsentReceiptRepository consentReceiptRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        TenantContext context = new TenantContext();
        context.setTenantId(tenantId);
        context.setUserId(userId);
        context.setRequestId("test-request");
        context.setTraceId("test-trace");
        TenantContextHolder.setContext(context);
    }

    @Test
    void wideningShouldInvalidateAndRequireReconsent() {
        CreateNoticeResponse notice = noticeManagementService.createNotice(
            new CreateNoticeRequest("marketing", "Marketing", "promo", "en"));
        CreateVersionResponse v1 = noticeManagementService.createVersion(
            notice.noticeId(), new CreateVersionRequest("v1"));
        noticeManagementService.addLanguage(
            notice.noticeId(), v1.versionId(), new AddLanguageRequest("en", "Email marketing notice"));
        PurposeScopeDto scopeV1 = new PurposeScopeDto(
            List.of("contact"),
            List.of("email"),
            List.of("internal"),
            "consent",
            365,
            List.of("marketing"),
            Map.of()
        );
        noticeManagementService.publishVersion(
            notice.noticeId(), v1.versionId(), new PublishVersionRequest(scopeV1));

        UUID principalId = UUID.randomUUID();
        GrantConsentResponse grantV1 = noticeManagementService.grantConsent(new GrantConsentRequest(
            principalId, "marketing", "en", "WIDGET", null, null, "idemp-1"));
        assertThat(grantV1.purposeVersionId()).isNotNull();

        CreateVersionResponse v2 = noticeManagementService.createVersion(
            notice.noticeId(), new CreateVersionRequest("v2"));
        noticeManagementService.addLanguage(
            notice.noticeId(), v2.versionId(), new AddLanguageRequest("en", "Email + phone"));
        PurposeScopeDto scopeV2 = new PurposeScopeDto(
            List.of("contact"),
            List.of("email", "phone"),
            List.of("internal"),
            "consent",
            365,
            List.of("marketing"),
            Map.of()
        );
        noticeManagementService.publishVersion(
            notice.noticeId(), v2.versionId(), new PublishVersionRequest(scopeV2));

        PurposeVersion pv2 = purposeVersionRepository
            .findByTenantIdAndNoticeVersionIdAndPurposeKey(tenantId, v2.versionId(), "marketing")
            .orElseThrow();

        assertThat(consentInvalidationRepository.existsByTenantIdAndConsentReceiptId(tenantId, grantV1.receiptId()))
            .isTrue();

        ReconsentRequirement requirement = reconsentRequirementRepository
            .findByTenantIdAndDataPrincipalIdAndRequiredPurposeVersionId(tenantId, principalId, pv2.getId())
            .orElseThrow();
        assertThat(requirement.getStatus().name()).isEqualTo("REQUIRED");

        ConsentValidityResponse validity = consentEvaluationService
            .validateConsent(principalId, "marketing", pv2.getId());
        assertThat(validity.valid()).isFalse();
        assertThat(validity.reason()).isEqualTo("RECONSENT_REQUIRED");

        GrantConsentResponse grantV2 = noticeManagementService.grantConsent(new GrantConsentRequest(
            principalId, "marketing", "en", "WIDGET", null, null, "idemp-2"));
        assertThat(grantV2.purposeVersionId()).isEqualTo(pv2.getId());

        ConsentValidityResponse validityAfter = consentEvaluationService
            .validateConsent(principalId, "marketing", pv2.getId());
        assertThat(validityAfter.valid()).isTrue();

        assertThat(countAudit("PURPOSE_WIDENED_DETECTED")).isGreaterThan(0);
        assertThat(countOutbox("PURPOSE_WIDENED_DETECTED")).isGreaterThan(0);
        assertThat(countOutbox("CONSENT_INVALIDATED_DUE_TO_PURPOSE_CHANGE")).isGreaterThan(0);
        assertThat(countOutbox("RECONSENT_REQUIRED")).isGreaterThan(0);
    }

    @Test
    void narrowingShouldNotInvalidate() {
        CreateNoticeResponse notice = noticeManagementService.createNotice(
            new CreateNoticeRequest("analytics", "Analytics", "analytics", "en"));
        CreateVersionResponse v1 = noticeManagementService.createVersion(
            notice.noticeId(), new CreateVersionRequest("v1"));
        noticeManagementService.addLanguage(
            notice.noticeId(), v1.versionId(), new AddLanguageRequest("en", "Analytics notice"));
        PurposeScopeDto scopeV1 = new PurposeScopeDto(
            List.of("usage"),
            List.of("email", "phone"),
            List.of("internal"),
            "consent",
            365,
            List.of("analytics"),
            Map.of()
        );
        noticeManagementService.publishVersion(
            notice.noticeId(), v1.versionId(), new PublishVersionRequest(scopeV1));

        UUID principalId = UUID.randomUUID();
        GrantConsentResponse grantV1 = noticeManagementService.grantConsent(new GrantConsentRequest(
            principalId, "analytics", "en", "WIDGET", null, null, "idemp-3"));

        CreateVersionResponse v2 = noticeManagementService.createVersion(
            notice.noticeId(), new CreateVersionRequest("v2"));
        noticeManagementService.addLanguage(
            notice.noticeId(), v2.versionId(), new AddLanguageRequest("en", "Analytics narrowed"));
        PurposeScopeDto scopeV2 = new PurposeScopeDto(
            List.of("usage"),
            List.of("email"),
            List.of("internal"),
            "consent",
            365,
            List.of("analytics"),
            Map.of()
        );
        noticeManagementService.publishVersion(
            notice.noticeId(), v2.versionId(), new PublishVersionRequest(scopeV2));

        ConsentReceiptEntity receipt = consentReceiptRepository.findById(grantV1.receiptId()).orElseThrow();
        assertThat(consentInvalidationRepository.existsByTenantIdAndConsentReceiptId(tenantId, receipt.getReceiptId()))
            .isFalse();

        PurposeVersion pv2 = purposeVersionRepository
            .findByTenantIdAndNoticeVersionIdAndPurposeKey(tenantId, v2.versionId(), "analytics")
            .orElseThrow();
        assertThat(reconsentRequirementRepository
            .findByTenantIdAndDataPrincipalIdAndRequiredPurposeVersionId(tenantId, principalId, pv2.getId()))
            .isEmpty();
    }

    private long countAudit(String action) {
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from consent.audit_events where action = ?",
            Long.class,
            action
        );
        return count != null ? count : 0L;
    }

    private long countOutbox(String eventType) {
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from consent.outbox_events where event_type = ?",
            Long.class,
            eventType
        );
        return count != null ? count : 0L;
    }
}
