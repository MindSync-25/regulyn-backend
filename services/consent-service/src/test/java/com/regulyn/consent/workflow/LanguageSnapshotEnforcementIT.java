package com.regulyn.consent.workflow;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.consent.ConsentServiceApplication;
import com.regulyn.consent.client.TranslationClient;
import com.regulyn.consent.entity.ConsentReceiptEntity;
import com.regulyn.consent.entity.NoticeLanguageText;
import com.regulyn.consent.model.*;
import com.regulyn.consent.repository.ConsentReceiptRepository;
import com.regulyn.consent.repository.NoticeLanguageTextRepository;
import com.regulyn.consent.repository.PurposeVersionRepository;
import com.regulyn.consent.service.ConsentEvaluationService;
import com.regulyn.consent.service.NoticeManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = ConsentServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("language-snapshot-test")
class LanguageSnapshotEnforcementIT {

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
    private NoticeLanguageTextRepository noticeLanguageTextRepository;

    @Autowired
    private ConsentReceiptRepository consentReceiptRepository;

    @Autowired
    private PurposeVersionRepository purposeVersionRepository;

    @Autowired
    private ConsentEvaluationService consentEvaluationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TestTranslationClient translationClient;

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

        translationClient.setTranslation("KN_TRANSLATED");
        translationClient.setEngine("test-engine");
        translationClient.setEngineVersion("v1");
    }

    @Test
    void translationGeneratedBeforeGrant() {
        CreateNoticeResponse notice = noticeManagementService.createNotice(
                new CreateNoticeRequest("marketing", "Marketing", "promo", "en"));
        CreateVersionResponse version = noticeManagementService.createVersion(
                notice.noticeId(), new CreateVersionRequest("v1"));
        noticeManagementService.addLanguage(
                notice.noticeId(), version.versionId(), new AddLanguageRequest("en", "English content"));
        noticeManagementService.publishVersion(notice.noticeId(), version.versionId());

        ResponseEntity<ActiveDualNoticeResponse> response = restTemplate.exchange(
                "/api/v2/consent/notices/active-dual?purpose=marketing&region=KA",
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders()),
                ActiveDualNoticeResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ActiveDualNoticeResponse dual = response.getBody();
        assertThat(dual).isNotNull();
        assertThat(dual.english().language()).isEqualTo("en");
        assertThat(dual.regional().language()).isEqualTo("kn");

        Optional<NoticeLanguageText> regionalRow = noticeLanguageTextRepository
                .findByTenantIdAndVersionIdAndLanguage(tenantId, version.versionId(), "kn");
        assertThat(regionalRow).isPresent();
        assertThat(regionalRow.get().getContentHash()).isEqualTo(dual.regional().contentHash());
        assertThat(regionalRow.get().getTranslationSource()).isEqualTo("AUTO");
        assertThat(regionalRow.get().getTranslationEngine()).isEqualTo("test-engine");
        assertThat(regionalRow.get().getTranslationEngineVersion()).isEqualTo("v1");

        assertThat(countAudit("CONSENT_LANGUAGE_TRANSLATION_GENERATED")).isEqualTo(1);
        assertThat(countOutbox("CONSENT_LANGUAGE_TRANSLATION_GENERATED")).isEqualTo(1);

        String auditPayload = jdbcTemplate.queryForObject(
                "select metadata::text from consent.audit_events where tenant_id = ? and action = ? limit 1",
                String.class,
                tenantId,
                "CONSENT_LANGUAGE_TRANSLATION_GENERATED"
        );
        String outboxPayload = jdbcTemplate.queryForObject(
                "select payload::text from consent.outbox_events where tenant_id = ? and event_type = ? limit 1",
                String.class,
                tenantId,
                "CONSENT_LANGUAGE_TRANSLATION_GENERATED"
        );
        assertThat(auditPayload).doesNotContain("English content");
        assertThat(auditPayload).doesNotContain("KN_TRANSLATED");
        assertThat(outboxPayload).doesNotContain("English content");
        assertThat(outboxPayload).doesNotContain("KN_TRANSLATED");

        GrantConsentResponse grant = noticeManagementService.grantConsent(new GrantConsentRequest(
                UUID.randomUUID(),
                "marketing",
                "en",
                "WIDGET",
                "KA",
                null,
                "idemp-1"
        ));

        ConsentReceiptEntity receipt = consentReceiptRepository.findById(grant.receiptId()).orElseThrow();
        assertThat(receipt.getRegionCode()).isEqualTo("KA");
        assertThat(receipt.getEnglishNoticeLanguageTextId()).isNotNull();
        assertThat(receipt.getEnglishContentHashSha256()).isNotNull();
        assertThat(receipt.getRegionalNoticeLanguageTextId()).isNotNull();
        assertThat(receipt.getRegionalContentHashSha256()).isNotNull();

        assertThat(countAudit("CONSENT_LANGUAGE_SNAPSHOT_STORED")).isEqualTo(1);
        assertThat(countOutbox("CONSENT_LANGUAGE_SNAPSHOT_STORED")).isEqualTo(1);

        String snapshotPayload = jdbcTemplate.queryForObject(
                "select payload::text from consent.outbox_events where tenant_id = ? and event_type = ? limit 1",
                String.class,
                tenantId,
                "CONSENT_LANGUAGE_SNAPSHOT_STORED"
        );
        assertThat(snapshotPayload).doesNotContain("English content");
        assertThat(snapshotPayload).doesNotContain("KN_TRANSLATED");
    }

    @Test
    void immutableTranslationDoesNotOverwrite() {
        CreateNoticeResponse notice = noticeManagementService.createNotice(
                new CreateNoticeRequest("shipping", "Shipping", "ops", "en"));
        CreateVersionResponse version = noticeManagementService.createVersion(
                notice.noticeId(), new CreateVersionRequest("v1"));
        noticeManagementService.addLanguage(
                notice.noticeId(), version.versionId(), new AddLanguageRequest("en", "English content"));
        noticeManagementService.addLanguage(
                notice.noticeId(), version.versionId(), new AddLanguageRequest("kn", "Existing KN"));
        noticeManagementService.publishVersion(notice.noticeId(), version.versionId());

        String existingHash = noticeLanguageTextRepository
                .findByTenantIdAndVersionIdAndLanguage(tenantId, version.versionId(), "kn")
                .orElseThrow()
                .getContentHash();

        long auditBefore = countAudit("CONSENT_LANGUAGE_TRANSLATION_GENERATED");
        long outboxBefore = countOutbox("CONSENT_LANGUAGE_TRANSLATION_GENERATED");

        translationClient.setTranslation("KN_TRANSLATED_NEW");

        ResponseEntity<ActiveDualNoticeResponse> response = restTemplate.exchange(
                "/api/v2/consent/notices/active-dual?purpose=shipping&region=KA",
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders()),
                ActiveDualNoticeResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String currentHash = noticeLanguageTextRepository
                .findByTenantIdAndVersionIdAndLanguage(tenantId, version.versionId(), "kn")
                .orElseThrow()
                .getContentHash();

        assertThat(currentHash).isEqualTo(existingHash);
        assertThat(countAudit("CONSENT_LANGUAGE_TRANSLATION_GENERATED")).isEqualTo(auditBefore);
        assertThat(countOutbox("CONSENT_LANGUAGE_TRANSLATION_GENERATED")).isEqualTo(outboxBefore);
    }

    @Test
    void failClosedWhenTranslationUnavailable() {
        CreateNoticeResponse notice = noticeManagementService.createNotice(
                new CreateNoticeRequest("retention", "Retention", "ops", "en"));
        CreateVersionResponse version = noticeManagementService.createVersion(
                notice.noticeId(), new CreateVersionRequest("v1"));
        noticeManagementService.addLanguage(
                notice.noticeId(), version.versionId(), new AddLanguageRequest("en", "English content"));
        noticeManagementService.publishVersion(notice.noticeId(), version.versionId());

        translationClient.setTranslation(null);

        assertThatThrownBy(() -> restTemplate.exchange(
                "/api/v2/consent/notices/active-dual?purpose=retention&region=KA",
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders()),
                ActiveDualNoticeResponse.class
        )).isInstanceOf(HttpStatusCodeException.class)
          .hasMessageContaining("503");

        assertThatThrownBy(() -> noticeManagementService.grantConsent(new GrantConsentRequest(
                UUID.randomUUID(),
                "retention",
                "en",
                "WIDGET",
                "KA",
                null,
                "idemp-2"
        ))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        assertThat(countReceiptsForTenant()).isEqualTo(0);
    }

    @Test
    void evaluationFailsClosedWhenSnapshotsMissing() {
        CreateNoticeResponse notice = noticeManagementService.createNotice(
                new CreateNoticeRequest("billing", "Billing", "finance", "en"));
        CreateVersionResponse version = noticeManagementService.createVersion(
                notice.noticeId(), new CreateVersionRequest("v1"));
        noticeManagementService.addLanguage(
                notice.noticeId(), version.versionId(), new AddLanguageRequest("en", "English content"));
        noticeManagementService.publishVersion(notice.noticeId(), version.versionId());

        UUID principalId = UUID.randomUUID();
        UUID purposeVersionId = purposeVersionRepository
                .findByTenantIdAndNoticeVersionIdAndPurposeKey(tenantId, version.versionId(), "billing")
                .orElseThrow()
                .getId();

        ConsentReceiptEntity receipt = new ConsentReceiptEntity();
        receipt.setTenantId(tenantId);
        receipt.setDataPrincipalId(principalId);
        receipt.setPurpose("billing");
        receipt.setSource("WIDGET");
        receipt.setStatus("GRANTED");
        receipt.setNoticeId(notice.noticeId());
        receipt.setVersionId(version.versionId());
        receipt.setVersionNumber(1);
        receipt.setLanguage("kn");
        receipt.setContentHash("hash");
        receipt.setReceiptHash("hash");
        receipt.setPurposeVersionId(purposeVersionId);
        receipt.setRegionCode("KA");
        consentReceiptRepository.save(receipt);

        ConsentValidityResponse validity = consentEvaluationService
                .validateConsent(principalId, "billing", purposeVersionId);

        assertThat(validity.valid()).isFalse();
        assertThat(validity.reason()).isEqualTo("LANGUAGE_SNAPSHOT_MISSING");
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        headers.set("X-User-ID", userId.toString());
        return headers;
    }

        private long countAudit(String action) {
        Long count = jdbcTemplate.queryForObject(
                                "select count(*) from consent.audit_events where tenant_id = ? and action = ?",
                Long.class,
                                tenantId,
                action
        );
        return count != null ? count : 0L;
    }

        private long countOutbox(String eventType) {
        Long count = jdbcTemplate.queryForObject(
                                "select count(*) from consent.outbox_events where tenant_id = ? and event_type = ?",
                Long.class,
                                tenantId,
                eventType
        );
        return count != null ? count : 0L;
    }

        private long countReceiptsForTenant() {
                Long count = jdbcTemplate.queryForObject(
                                "select count(*) from consent.consent_receipts where tenant_id = ?",
                                Long.class,
                                tenantId
                );
                return count != null ? count : 0L;
        }

    @TestConfiguration
        @Profile("language-snapshot-test")
    static class TranslationClientTestConfig {
        @Bean
        @Primary
        TestTranslationClient translationClient() {
            return new TestTranslationClient();
        }
    }

    static class TestTranslationClient implements TranslationClient {
        private final AtomicReference<String> translation = new AtomicReference<>();
                private final AtomicReference<String> engine = new AtomicReference<>();
                private final AtomicReference<String> engineVersion = new AtomicReference<>();

        void setTranslation(String value) {
            translation.set(value);
        }

                void setEngine(String value) {
                        engine.set(value);
                }

                void setEngineVersion(String value) {
                        engineVersion.set(value);
                }

        @Override
        public String translate(String text, String fromLanguage, String toLanguage) {
            return translation.get();
        }

                @Override
                public String getEngine() {
                        return engine.get();
                }

                @Override
                public String getEngineVersion() {
                        return engineVersion.get();
                }
    }
}
