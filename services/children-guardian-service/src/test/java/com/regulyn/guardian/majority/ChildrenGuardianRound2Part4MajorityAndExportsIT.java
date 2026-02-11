package com.regulyn.guardian.majority;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.regulyn.guardian.config.TestPermitAllSecurityConfig;
import com.regulyn.guardian.config.TestSecurityConfig;
import com.regulyn.guardian.dto.AdultConsentLinkRequest;
import com.regulyn.guardian.dto.EvidenceExportRequest;
import com.regulyn.guardian.dto.MajorityCheckRequest;
import com.regulyn.guardian.dto.MajorityCheckResponse;
import com.regulyn.guardian.entity.Child;
import com.regulyn.guardian.entity.Guardian;
import com.regulyn.guardian.entity.GuardianConsent;
import com.regulyn.guardian.repository.ChildRepository;
import com.regulyn.guardian.repository.GuardianConsentRepository;
import com.regulyn.guardian.repository.GuardianRepository;
import com.regulyn.guardian.service.ConsentStateMachine;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import({TestSecurityConfig.class, TestPermitAllSecurityConfig.class})
class ChildrenGuardianRound2Part4MajorityAndExportsIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    private static final WireMockServer wireMock = new WireMockServer(0);

    static {
        wireMock.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.schemas", () -> "public,guardian,children");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.task.scheduling.enabled", () -> false);
        registry.add("audit.schema", () -> "guardian");
        registry.add("notification.service.baseUrl", wireMock::baseUrl);
        registry.add("evidence.reporting.baseUrl", wireMock::baseUrl);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChildRepository childRepository;

    @Autowired
    private GuardianRepository guardianRepository;

    @Autowired
    private GuardianConsentRepository consentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @AfterAll
    static void shutdownWireMock() {
        wireMock.stop();
    }

    @Test
    void majorityTransitionIsIdempotent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        wireMock.stubFor(post(urlEqualTo("/notifications/email"))
                .willReturn(aResponse().withStatus(200)));

        Child child = seedChild(tenantId, LocalDate.now().minusYears(19), "US", "CA");
        Guardian guardian = seedGuardian(tenantId, child.getChildId());
        seedApprovedConsent(tenantId, child.getChildId(), guardian.getGuardianId());

        ResponseEntity<MajorityCheckResponse> first = restTemplate.exchange(
                "/children/" + child.getChildId() + "/majority-check",
                HttpMethod.POST,
                new HttpEntity<>(new MajorityCheckRequest(LocalDate.now()), tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"))),
                MajorityCheckResponse.class
        );

        ResponseEntity<MajorityCheckResponse> second = restTemplate.exchange(
                "/children/" + child.getChildId() + "/majority-check",
                HttpMethod.POST,
                new HttpEntity<>(new MajorityCheckRequest(LocalDate.now()), tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"))),
                MajorityCheckResponse.class
        );

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer transitionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM children.child_majority_transitions WHERE tenant_id = ? AND child_id = ?",
                Integer.class,
                tenantId,
                child.getChildId()
        );
        assertThat(transitionCount).isEqualTo(1);

        Integer auditReached = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM guardian.audit_events WHERE tenant_id = ? AND action = ?",
                Integer.class,
                tenantId,
                "CHILD_MAJORITY_THRESHOLD_REACHED"
        );
        assertThat(auditReached).isEqualTo(1);

        Integer outboxReached = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId,
                "child.majority_threshold_reached"
        );
        assertThat(outboxReached).isEqualTo(1);

        Integer revokeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM guardian.audit_events WHERE tenant_id = ? AND action = ?",
                Integer.class,
                tenantId,
                "GUARDIAN_AUTHORITY_REVOKED_DUE_TO_MAJORITY"
        );
        assertThat(revokeCount).isEqualTo(1);

        Integer adultRequired = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM guardian.audit_events WHERE tenant_id = ? AND action = ?",
                Integer.class,
                tenantId,
                "ADULT_CONSENT_REQUIRED"
        );
        assertThat(adultRequired).isEqualTo(1);
    }

    @Test
    void notificationSentOnTransition() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        wireMock.stubFor(post(urlEqualTo("/notifications/email"))
                .willReturn(aResponse().withStatus(200)));

        Child child = seedChild(tenantId, LocalDate.now().minusYears(19), "US", "CA");
        Guardian guardian = seedGuardian(tenantId, child.getChildId());
        seedApprovedConsent(tenantId, child.getChildId(), guardian.getGuardianId());

        restTemplate.exchange(
                "/children/" + child.getChildId() + "/majority-check",
                HttpMethod.POST,
                new HttpEntity<>(new MajorityCheckRequest(LocalDate.now()), tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"))),
                String.class
        );

        wireMock.verify(1, postRequestedFor(urlEqualTo("/notifications/email")));
    }

    @Test
    void adultConsentLinkIsIdempotent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        wireMock.stubFor(post(urlEqualTo("/notifications/email"))
                .willReturn(aResponse().withStatus(200)));

        Child child = seedChild(tenantId, LocalDate.now().minusYears(19), "US", "CA");
        Guardian guardian = seedGuardian(tenantId, child.getChildId());
        seedApprovedConsent(tenantId, child.getChildId(), guardian.getGuardianId());

        restTemplate.exchange(
                "/children/" + child.getChildId() + "/majority-check",
                HttpMethod.POST,
                new HttpEntity<>(new MajorityCheckRequest(LocalDate.now()), tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"))),
                String.class
        );

        AdultConsentLinkRequest request = new AdultConsentLinkRequest("receipt-1", "sha-1");

        ResponseEntity<String> first = restTemplate.exchange(
                "/children/" + child.getChildId() + "/adult-consent-link",
                HttpMethod.POST,
                new HttpEntity<>(request, tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"))),
                String.class
        );
        ResponseEntity<String> second = restTemplate.exchange(
                "/children/" + child.getChildId() + "/adult-consent-link",
                HttpMethod.POST,
                new HttpEntity<>(request, tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"))),
                String.class
        );

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> conflict = restTemplate.exchange(
                "/children/" + child.getChildId() + "/adult-consent-link",
                HttpMethod.POST,
                new HttpEntity<>(new AdultConsentLinkRequest("receipt-2", "sha-2"), tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"))),
                String.class
        );

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void evidenceBundleExportStored() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        wireMock.stubFor(post(urlEqualTo("/evidence/bundles"))
                .willReturn(okJson("{\"bundleRef\":\"bundles/child/xyz\",\"bundleSha256\":\"sha-xyz\"}")));

        Child child = seedChild(tenantId, LocalDate.now().minusYears(10), "US", "CA");
        Guardian guardian = seedGuardian(tenantId, child.getChildId());
        seedApprovedConsent(tenantId, child.getChildId(), guardian.getGuardianId());

        EvidenceExportRequest request = new EvidenceExportRequest("CHILD_ALL");
        HttpHeaders headers = tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"));
        headers.set("X-Idempotency-Key", "exp-1");

        ResponseEntity<String> response = restTemplate.exchange(
                "/children/" + child.getChildId() + "/evidence-exports",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer exportCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM children.children_evidence_exports WHERE tenant_id = ? AND child_id = ?",
                Integer.class,
                tenantId,
                child.getChildId()
        );
        assertThat(exportCount).isEqualTo(1);

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM guardian.audit_events WHERE tenant_id = ? AND action = ?",
                Integer.class,
                tenantId,
                "CHILD_EVIDENCE_BUNDLE_CREATED"
        );
        assertThat(auditCount).isGreaterThan(0);

        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId,
                "child.evidence_bundle_created"
        );
        assertThat(outboxCount).isGreaterThan(0);
    }

    @Test
    void evidenceBundleExportFailureRecorded() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        wireMock.stubFor(post(urlEqualTo("/evidence/bundles"))
                .willReturn(aResponse().withStatus(503)));

        Child child = seedChild(tenantId, LocalDate.now().minusYears(10), "US", "CA");
        Guardian guardian = seedGuardian(tenantId, child.getChildId());
        seedApprovedConsent(tenantId, child.getChildId(), guardian.getGuardianId());

        EvidenceExportRequest request = new EvidenceExportRequest("CHILD_ALL");
        HttpHeaders headers = tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"));
        headers.set("X-Idempotency-Key", "exp-2");

        ResponseEntity<String> response = restTemplate.exchange(
                "/children/" + child.getChildId() + "/evidence-exports",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        Integer failedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM children.children_evidence_exports WHERE tenant_id = ? AND child_id = ? AND status = 'FAILED'",
                Integer.class,
                tenantId,
                child.getChildId()
        );
        assertThat(failedCount).isEqualTo(1);
    }

    private Child seedChild(UUID tenantId, LocalDate dob, String country, String state) {
        Child child = new Child();
        child.setTenantId(tenantId);
        child.setChildRef("CHILD-" + UUID.randomUUID());
        child.setFullName("Test Child");
        child.setDateOfBirth(dob);
        child.setCountry(country);
        child.setRegionCountryCode(country);
        child.setRegionStateCode(state);
        child.setStatus("ACTIVE");
        return childRepository.save(child);
    }

    private Guardian seedGuardian(UUID tenantId, UUID childId) {
        Guardian guardian = new Guardian();
        guardian.setTenantId(tenantId);
        guardian.setChildId(childId);
        guardian.setGuardianName("Test Guardian");
        guardian.setGuardianEmail("guardian@example.com");
        guardian.setGuardianPhone("+10000000000");
        guardian.setRelationship("PARENT");
        guardian.setStatus(ConsentStateMachine.GUARDIAN_VERIFIED);
        guardian.setVerifiedAt(Instant.now());
        guardian.setVerifiedBy(UUID.randomUUID());
        return guardianRepository.save(guardian);
    }

    private GuardianConsent seedApprovedConsent(UUID tenantId, UUID childId, UUID guardianId) {
        GuardianConsent consent = new GuardianConsent();
        consent.setTenantId(tenantId);
        consent.setChildId(childId);
        consent.setGuardianId(guardianId);
        consent.setPurposeKey("DATA_PROCESSING");
        consent.setConsentScope("FULL");
        consent.setStatus(ConsentStateMachine.STATUS_APPROVED);
        consent.setRequiresApproval(true);
        consent.setApprovedAt(Instant.now());
        consent.setApprovedBy(UUID.randomUUID());
        consent.setValidFrom(LocalDate.now().minusYears(1));
        consent.setValidTo(LocalDate.now().plusYears(1));
        consent.setRegionCountryCode("US");
        consent.setRegionStateCode("CA");
        consent.setThresholdAgeYears((short) 18);
        return consentRepository.save(consent);
    }

    private HttpHeaders tenantContextHeaders(UUID tenantId, UUID userId, Set<String> roles) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        Map<String, Object> contextMap = Map.of(
                "tenantId", tenantId.toString(),
                "userId", userId.toString(),
                "roles", roles
        );
        headers.set("X-Tenant-Context", objectMapper.writeValueAsString(contextMap));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
