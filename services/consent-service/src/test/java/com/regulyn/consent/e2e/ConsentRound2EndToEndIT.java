package com.regulyn.consent.e2e;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.consent.ConsentServiceApplication;
import com.regulyn.consent.client.EvidenceClient;
import com.regulyn.consent.client.TranslationClient;
import com.regulyn.consent.entity.ConsentReceiptEntity;
import com.regulyn.consent.entity.NoticeLanguageText;
import com.regulyn.consent.entity.ReconsentRequirement;
import com.regulyn.consent.entity.ReconsentStatus;
import com.regulyn.consent.model.*;
import com.regulyn.consent.repository.*;
import com.regulyn.consent.service.CommunicationConsentService;
import com.regulyn.consent.service.NoticeManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpStatusCodeException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = ConsentServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("e2e-test")
class ConsentRound2EndToEndIT {

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
    private CommunicationConsentService communicationConsentService;

    @Autowired
    private PurposeVersionRepository purposeVersionRepository;

    @Autowired
    private ConsentReceiptRepository consentReceiptRepository;

    @Autowired
    private ConsentInvalidationRepository consentInvalidationRepository;

    @Autowired
    private ReconsentRequirementRepository reconsentRequirementRepository;

    @Autowired
    private NoticeLanguageTextRepository noticeLanguageTextRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TestEvidenceClient evidenceClient;

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

        evidenceClient.reset();
        translationClient.reset();
    }

    @Test
    void scenario1_happyPathPublishDualGrantValid() {
        CreateNoticeResponse notice = noticeManagementService.createNotice(
                new CreateNoticeRequest("marketing", "Marketing", "promo", "en"));
        CreateVersionResponse version = noticeManagementService.createVersion(
                notice.noticeId(), new CreateVersionRequest("v1"));
        noticeManagementService.addLanguage(
                notice.noticeId(), version.versionId(), new AddLanguageRequest("en", "English marketing content"));

        PurposeScopeDto scope = new PurposeScopeDto(
                List.of("contact"),
                List.of("email"),
                List.of("self"),
                "consent",
                30,
                List.of("marketing"),
                Map.of()
        );
        noticeManagementService.publishVersion(notice.noticeId(), version.versionId(), new PublishVersionRequest(scope));

        assertThat(countAudit("PURPOSE_VERSION_CREATED")).isGreaterThan(0);
        assertThat(countOutbox("PURPOSE_VERSION_CREATED")).isGreaterThan(0);

        translationClient.setTranslation("KN_TRANSLATED");
        translationClient.setEngine("test-engine");
        translationClient.setEngineVersion("v1");

        ResponseEntity<ActiveDualNoticeResponse> dualResponse = restTemplate.exchange(
                "/api/v2/consent/notices/active-dual?purpose=marketing&region=KA",
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders()),
                ActiveDualNoticeResponse.class
        );

        assertThat(dualResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        ActiveDualNoticeResponse dual = dualResponse.getBody();
        assertThat(dual).isNotNull();
        assertThat(dual.english().language()).isEqualTo("en");
        assertThat(dual.regional().language()).isEqualTo("kn");

        NoticeLanguageText regionalText = noticeLanguageTextRepository
                .findByTenantIdAndVersionIdAndLanguage(tenantId, version.versionId(), "kn")
                .orElseThrow();
        assertThat(regionalText.getTranslationSource()).isEqualTo("AUTO");
        assertThat(regionalText.getTranslationEngine()).isEqualTo("test-engine");
        assertThat(regionalText.getTranslationEngineVersion()).isEqualTo("v1");

        assertThat(countAudit("CONSENT_LANGUAGE_TRANSLATION_GENERATED")).isEqualTo(1);
        assertThat(countOutbox("CONSENT_LANGUAGE_TRANSLATION_GENERATED")).isEqualTo(1);

        GrantConsentResponse grant = noticeManagementService.grantConsent(new GrantConsentRequest(
                UUID.randomUUID(),
                "marketing",
                "en",
                "WIDGET",
                "KA",
                null,
                "idemp-marketing-1"
        ));

        ConsentReceiptEntity receipt = consentReceiptRepository.findById(grant.receiptId()).orElseThrow();
        assertThat(receipt.getRegionCode()).isEqualTo("KA");
        assertThat(receipt.getEnglishNoticeLanguageTextId()).isNotNull();
        assertThat(receipt.getEnglishContentHashSha256()).isNotNull();
        assertThat(receipt.getRegionalNoticeLanguageTextId()).isNotNull();
        assertThat(receipt.getRegionalContentHashSha256()).isNotNull();
        assertThat(receipt.getPurposeVersionId()).isNotNull();

        assertThat(countOutbox("CONSENT_LANGUAGE_SNAPSHOT_STORED")).isEqualTo(1);

        UUID purposeVersionId = purposeVersionRepository
                .findByTenantIdAndNoticeVersionIdAndPurposeKey(tenantId, version.versionId(), "marketing")
                .orElseThrow()
                .getId();

        ResponseEntity<ConsentValidityResponse> validity = restTemplate.exchange(
                "/api/v2/consent/consents/valid?dataPrincipalId=" + receipt.getDataPrincipalId()
                        + "&purpose=marketing&requiredPurposeVersionId=" + purposeVersionId,
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders()),
                ConsentValidityResponse.class
        );

        assertThat(validity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(validity.getBody()).isNotNull();
        assertThat(validity.getBody().valid()).isTrue();
    }

    @Test
    void scenario2_idempotentGrantDoesNotDuplicate() {
        SeededNotice seeded = seedNotice("idempotent", "Idempotent");
        translationClient.setTranslation("KN_TRANSLATED");

        GrantConsentRequest request = new GrantConsentRequest(
                seeded.principalId,
                "idempotent",
                "en",
                "WIDGET",
                "KA",
                null,
                "idemp-key"
        );

        GrantConsentResponse first = noticeManagementService.grantConsent(request);
        long receiptsBefore = countReceiptsForTenant();
        long auditBefore = countAudit("consent.granted");
        long outboxBefore = countOutbox("consent.granted");

        GrantConsentResponse second = noticeManagementService.grantConsent(request);

        assertThat(second.receiptId()).isEqualTo(first.receiptId());
        assertThat(countReceiptsForTenant()).isEqualTo(receiptsBefore);
        assertThat(countAudit("consent.granted")).isEqualTo(auditBefore);
        assertThat(countOutbox("consent.granted")).isEqualTo(outboxBefore);
    }

    @Test
    void scenario3_wideningInvalidatesUntilReconsent() {
        SeededNotice seeded = seedNotice("widening", "Widening");
        translationClient.setTranslation("KN_TRANSLATED");

        GrantConsentResponse initial = noticeManagementService.grantConsent(new GrantConsentRequest(
                seeded.principalId,
                "widening",
                "en",
                "WIDGET",
                "KA",
                null,
                "idemp-widen-1"
        ));

        CreateVersionResponse v2 = noticeManagementService.createVersion(
                seeded.noticeId, new CreateVersionRequest("v2"));
        noticeManagementService.addLanguage(
                seeded.noticeId, v2.versionId(), new AddLanguageRequest("en", "Widened"));

        PurposeScopeDto widened = new PurposeScopeDto(
                List.of("contact"),
                List.of("email", "phone"),
                List.of("self"),
                "consent",
                60,
                List.of("marketing"),
                Map.of()
        );
        noticeManagementService.publishVersion(seeded.noticeId, v2.versionId(), new PublishVersionRequest(widened));

        assertThat(countOutbox("PURPOSE_WIDENED_DETECTED")).isGreaterThan(0);
        assertThat(consentInvalidationRepository.existsByTenantIdAndConsentReceiptId(tenantId, initial.receiptId())).isTrue();

        UUID pv2 = purposeVersionRepository
                .findByTenantIdAndNoticeVersionIdAndPurposeKey(tenantId, v2.versionId(), "widening")
                .orElseThrow()
                .getId();

        Optional<ReconsentRequirement> requirement = reconsentRequirementRepository
                .findByTenantIdAndDataPrincipalIdAndRequiredPurposeVersionId(tenantId, seeded.principalId, pv2);
        assertThat(requirement).isPresent();
        assertThat(requirement.get().getStatus()).isEqualTo(ReconsentStatus.REQUIRED);

        ResponseEntity<ConsentValidityResponse> validity = restTemplate.exchange(
                "/api/v2/consent/consents/valid?dataPrincipalId=" + seeded.principalId
                        + "&purpose=widening&requiredPurposeVersionId=" + pv2,
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders()),
                ConsentValidityResponse.class
        );

        assertThat(validity.getBody()).isNotNull();
        assertThat(validity.getBody().valid()).isFalse();
        assertThat(validity.getBody().reason()).isEqualTo("RECONSENT_REQUIRED");

        GrantConsentResponse after = noticeManagementService.grantConsent(new GrantConsentRequest(
                seeded.principalId,
                "widening",
                "en",
                "WIDGET",
                "KA",
                null,
                "idemp-widen-2"
        ));

        ConsentReceiptEntity newReceipt = consentReceiptRepository.findById(after.receiptId()).orElseThrow();
        assertThat(newReceipt.getPurposeVersionId()).isEqualTo(pv2);
        assertThat(newReceipt.getRegionalNoticeLanguageTextId()).isNotNull();
        assertThat(countOutbox("RECONSENT_SATISFIED")).isGreaterThan(0);

        ResponseEntity<ConsentValidityResponse> validityAfter = restTemplate.exchange(
                "/api/v2/consent/consents/valid?dataPrincipalId=" + seeded.principalId
                        + "&purpose=widening&requiredPurposeVersionId=" + pv2,
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders()),
                ConsentValidityResponse.class
        );

        assertThat(validityAfter.getBody()).isNotNull();
        assertThat(validityAfter.getBody().valid()).isTrue();
    }

    @Test
    void scenario4_narrowingDoesNotInvalidate() {
        SeededNotice seeded = seedNotice("narrowing", "Narrowing");
        translationClient.setTranslation("KN_TRANSLATED");

        GrantConsentResponse initial = noticeManagementService.grantConsent(new GrantConsentRequest(
                seeded.principalId,
                "narrowing",
                "en",
                "WIDGET",
                "KA",
                null,
                "idemp-narrow-1"
        ));

        long invalidationsBefore = countInvalidations();
        long reconsentBefore = countReconsentRequirements();

        CreateVersionResponse v2 = noticeManagementService.createVersion(
                seeded.noticeId, new CreateVersionRequest("v2"));
        noticeManagementService.addLanguage(
                seeded.noticeId, v2.versionId(), new AddLanguageRequest("en", "Narrowed"));

        PurposeScopeDto narrowed = new PurposeScopeDto(
                List.of("contact"),
                List.of("email"),
                List.of("self"),
                "consent",
                15,
                List.of("marketing"),
                Map.of()
        );
        noticeManagementService.publishVersion(seeded.noticeId, v2.versionId(), new PublishVersionRequest(narrowed));

        assertThat(countInvalidations()).isEqualTo(invalidationsBefore);
        assertThat(countReconsentRequirements()).isEqualTo(reconsentBefore);
        assertThat(consentInvalidationRepository.existsByTenantIdAndConsentReceiptId(tenantId, initial.receiptId())).isFalse();
    }

    @Test
    void scenario5_communicationConsentFlow() {
        UUID principalA = UUID.randomUUID();
        UUID principalB = UUID.randomUUID();
        UUID principalC = UUID.randomUUID();

        Instant effectiveAt = Instant.parse("2026-02-07T10:15:30Z");
        CommunicationConsentRequest optInRequest = new CommunicationConsentRequest(
                principalA,
                effectiveAt,
                "WIDGET",
                "en",
                null,
                sha256("COMM_TEXT"),
                null,
                null,
                null,
                null,
                null
        );

        CommunicationConsentResponse optIn = communicationConsentService.recordOptIn(
                com.regulyn.consent.entity.CommunicationChannel.EMAIL,
                optInRequest
        );

        assertThat(optIn.deduped()).isFalse();
        assertThat(optIn.evidenceArtifactId()).isNotNull();
        assertThat(countLedgerRows(principalA, "EMAIL", "OPTED_IN", Instant.parse("2026-02-07T10:15:00Z"))).isEqualTo(1);
        assertThat(countOutbox("COMMUNICATION_OPT_IN")).isGreaterThan(0);

        CommunicationConsentResponse deduped = communicationConsentService.recordOptIn(
                com.regulyn.consent.entity.CommunicationChannel.EMAIL,
                optInRequest
        );
        assertThat(deduped.deduped()).isTrue();
        assertThat(evidenceClient.getCallCount()).isEqualTo(1);

        communicationConsentService.recordOptOut(
                com.regulyn.consent.entity.CommunicationChannel.EMAIL,
                new CommunicationConsentRequest(principalA, Instant.parse("2026-02-07T10:20:00Z"), "WIDGET", "en", null, sha256("COMM_TEXT"), null, null, null, null, null)
        );

        communicationConsentService.recordOptIn(
                com.regulyn.consent.entity.CommunicationChannel.EMAIL,
                new CommunicationConsentRequest(principalC, Instant.parse("2026-02-07T10:10:00Z"), "WIDGET", "en", null, sha256("COMM_TEXT"), null, null, null, null, null)
        );

        CommunicationConsentBatchResponse batch = communicationConsentService.batchStatus(
                com.regulyn.consent.entity.CommunicationChannel.EMAIL,
                List.of(principalA, principalB, principalC)
        );

        Map<UUID, CommunicationConsentBatchResult> byPrincipal = batch.results().stream()
                .collect(java.util.stream.Collectors.toMap(CommunicationConsentBatchResult::dataPrincipalId, r -> r));

        assertThat(byPrincipal.get(principalA).allowed()).isFalse();
        assertThat(byPrincipal.get(principalB).allowed()).isFalse();
        assertThat(byPrincipal.get(principalB).state()).isEqualTo("UNKNOWN");
        assertThat(byPrincipal.get(principalC).allowed()).isTrue();
    }

    @Test
    void scenario6_translationUnavailableBlocksDualAndGrant() {
        CreateNoticeResponse notice = noticeManagementService.createNotice(
                new CreateNoticeRequest("blocked", "Blocked", "ops", "en"));
        CreateVersionResponse version = noticeManagementService.createVersion(
                notice.noticeId(), new CreateVersionRequest("v1"));
        noticeManagementService.addLanguage(
                notice.noticeId(), version.versionId(), new AddLanguageRequest("en", "English content"));
        noticeManagementService.publishVersion(notice.noticeId(), version.versionId());

        translationClient.setTranslation(null);

        assertThatThrownBy(() -> restTemplate.exchange(
                "/api/v2/consent/notices/active-dual?purpose=blocked&region=KA",
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders()),
                ActiveDualNoticeResponse.class
        )).isInstanceOf(HttpStatusCodeException.class)
          .hasMessageContaining("503");

        assertThat(noticeLanguageTextRepository
                .findByTenantIdAndVersionIdAndLanguage(tenantId, version.versionId(), "kn"))
                .isEmpty();

        assertThatThrownBy(() -> noticeManagementService.grantConsent(new GrantConsentRequest(
                UUID.randomUUID(),
                "blocked",
                "en",
                "WIDGET",
                "KA",
                null,
                "idemp-blocked"
        ))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        assertThat(countReceiptsForTenant()).isEqualTo(0);
    }

    @Test
    void scenario7_noRawContentInAuditOutbox() {
        SeededNotice seeded = seedNotice("content", "ContentTest");
        translationClient.setTranslation("KN_TRANSLATED");
        noticeManagementService.grantConsent(new GrantConsentRequest(
                seeded.principalId,
                "content",
                "en",
                "WIDGET",
                "KA",
                null,
                "idemp-content"
        ));

        List<String> auditRows = jdbcTemplate.queryForList(
                "select metadata::text from consent.audit_events where tenant_id = ? and action in (" +
                        "'CONSENT_LANGUAGE_TRANSLATION_GENERATED','CONSENT_LANGUAGE_SNAPSHOT_STORED'," +
                        "'PURPOSE_VERSION_CREATED','PURPOSE_WIDENED_DETECTED'," +
                        "'CONSENT_INVALIDATED_DUE_TO_PURPOSE_CHANGE','RECONSENT_REQUIRED')",
                String.class,
                tenantId
        );
        List<String> outboxRows = jdbcTemplate.queryForList(
                "select payload::text from consent.outbox_events where tenant_id = ? and event_type in (" +
                        "'CONSENT_LANGUAGE_TRANSLATION_GENERATED','CONSENT_LANGUAGE_SNAPSHOT_STORED'," +
                        "'PURPOSE_VERSION_CREATED','PURPOSE_WIDENED_DETECTED'," +
                        "'CONSENT_INVALIDATED_DUE_TO_PURPOSE_CHANGE','RECONSENT_REQUIRED')",
                String.class,
                tenantId
        );

        auditRows.forEach(row -> {
            assertThat(row).doesNotContain("English marketing content");
            assertThat(row).doesNotContain("KN_TRANSLATED");
            assertThat(row).doesNotContain("\"content\"");
        });
        outboxRows.forEach(row -> {
            assertThat(row).doesNotContain("English marketing content");
            assertThat(row).doesNotContain("KN_TRANSLATED");
            assertThat(row).doesNotContain("\"content\"");
        });
    }

    private SeededNotice seedNotice(String purpose, String title) {
        CreateNoticeResponse notice = noticeManagementService.createNotice(
                new CreateNoticeRequest(purpose, title, "promo", "en"));
        CreateVersionResponse version = noticeManagementService.createVersion(
                notice.noticeId(), new CreateVersionRequest("v1"));
        noticeManagementService.addLanguage(
                notice.noticeId(), version.versionId(), new AddLanguageRequest("en", "English marketing content"));

        PurposeScopeDto scope = new PurposeScopeDto(
                List.of("contact"),
                List.of("email"),
                List.of("self"),
                "consent",
                30,
                List.of("marketing"),
                Map.of()
        );
        noticeManagementService.publishVersion(notice.noticeId(), version.versionId(), new PublishVersionRequest(scope));

        return new SeededNotice(notice.noticeId(), version.versionId(), UUID.randomUUID());
    }

    private record SeededNotice(UUID noticeId, UUID versionId, UUID principalId) {}

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

    private long countInvalidations() {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from consent.consent_invalidations where tenant_id = ?",
                Long.class,
                tenantId
        );
        return count != null ? count : 0L;
    }

    private long countReconsentRequirements() {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from consent.reconsent_requirements where tenant_id = ?",
                Long.class,
                tenantId
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

    private long countLedgerRows(UUID principalId, String channel, String state, Instant bucket) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from consent.communication_consent_ledger where tenant_id = ? and data_principal_id = ? and channel = ? and state = ? and effective_time_bucket = ?",
                Long.class,
                tenantId,
                principalId,
                channel,
                state,
                java.sql.Timestamp.from(bucket)
        );
        return count != null ? count : 0L;
    }

    private String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    @TestConfiguration
        @Profile("e2e-test")
    static class ExternalClientTestConfig {
        @Bean
        @Primary
        TestEvidenceClient evidenceClient() {
            return new TestEvidenceClient();
        }

        @Bean
        @Primary
        TestTranslationClient translationClient() {
            return new TestTranslationClient();
        }
    }

    static class TestEvidenceClient implements EvidenceClient {
        private final AtomicInteger callCount = new AtomicInteger();

        void reset() {
            callCount.set(0);
        }

        int getCallCount() {
            return callCount.get();
        }

        @Override
        public UUID createArtifact(UUID tenantId, String artifactType, Map<String, Object> metadata) {
            callCount.incrementAndGet();
            return UUID.fromString("11111111-1111-1111-1111-111111111111");
        }
    }

    static class TestTranslationClient implements TranslationClient {
        private final AtomicReference<String> translation = new AtomicReference<>();
        private final AtomicReference<String> engine = new AtomicReference<>();
        private final AtomicReference<String> engineVersion = new AtomicReference<>();

        void reset() {
            translation.set("KN_TRANSLATED");
            engine.set("test-engine");
            engineVersion.set("v1");
        }

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
