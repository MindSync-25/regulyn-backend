package com.regulyn.guardian.esign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.regulyn.guardian.config.TestPermitAllSecurityConfig;
import com.regulyn.guardian.config.TestSecurityConfig;
import com.regulyn.guardian.dto.ApproveConsentRequest;
import com.regulyn.guardian.dto.CreateEsignRequestRequest;
import com.regulyn.guardian.dto.CreateEsignRequestResponse;
import com.regulyn.guardian.entity.Child;
import com.regulyn.guardian.entity.Guardian;
import com.regulyn.guardian.entity.GuardianConsent;
import com.regulyn.guardian.repository.ChildRepository;
import com.regulyn.guardian.repository.ConsentSignedArtifactRepository;
import com.regulyn.guardian.repository.EsignRequestRepository;
import com.regulyn.guardian.repository.EsignWebhookEventRepository;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import({TestSecurityConfig.class, TestPermitAllSecurityConfig.class})
class EsignIntegrationIT {

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
        registry.add("children.esign.stub.hmacSecret", () -> "test-secret");
        registry.add("children.esign.defaultProvider", () -> "STUB");
        registry.add("evidence.service.baseUrl", wireMock::baseUrl);
        registry.add("audit.schema", () -> "guardian");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GuardianRepository guardianRepository;

        @Autowired
        private ChildRepository childRepository;

    @Autowired
    private GuardianConsentRepository consentRepository;

    @Autowired
    private EsignRequestRepository esignRequestRepository;

    @Autowired
    private ConsentSignedArtifactRepository artifactRepository;

    @Autowired
    private EsignWebhookEventRepository webhookEventRepository;

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
    void esignRequestIsIdempotentAndAudited() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        GuardianConsent consent = seedConsent(tenantId);

        CreateEsignRequestRequest request = new CreateEsignRequestRequest("STUB", "GUARDIAN_CONSENT", 1, "https://return.local");
        HttpHeaders headers = tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"));
        headers.set("X-Idempotency-Key", "k1");

        ResponseEntity<CreateEsignRequestResponse> response = restTemplate.exchange(
                "/consents/" + consent.getConsentId() + "/esign-requests",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                CreateEsignRequestResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CreateEsignRequestResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.signingUrl()).contains("stub-esign.local");

        assertThat(esignRequestRepository.findById(body.esignRequestId())).isPresent();

        GuardianConsent reloaded = consentRepository.findByTenantIdAndConsentId(tenantId, consent.getConsentId()).orElseThrow();
        assertThat(reloaded.getEsignRequestId()).isEqualTo(body.esignRequestId());

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM guardian.audit_events WHERE tenant_id = ? AND action = ?",
                Integer.class,
                tenantId,
                "ESIGN_REQUEST_CREATED"
        );
        assertThat(auditCount).isNotNull();
        assertThat(auditCount).isGreaterThan(0);

        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId,
                "esign.request_created"
        );
        assertThat(outboxCount).isNotNull();
        assertThat(outboxCount).isGreaterThan(0);

        ResponseEntity<CreateEsignRequestResponse> second = restTemplate.exchange(
                "/consents/" + consent.getConsentId() + "/esign-requests",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                CreateEsignRequestResponse.class
        );
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().esignRequestId()).isEqualTo(body.esignRequestId());
    }

    @Test
    void webhookSignatureFailureDoesNotStoreArtifact() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        GuardianConsent consent = seedConsent(tenantId);

        CreateEsignRequestResponse esignResponse = createEsignRequest(tenantId, userId, consent, "k2");

        String payload = buildWebhookPayload("evt-1", esignResponse.providerEnvelopeId(), "DOCUMENT_SIGNED", "fake-pdf");
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Stub-Signature", "bad-signature");

        ResponseEntity<String> webhookResponse = restTemplate.exchange(
                "/esign/webhooks/STUB",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                String.class
        );

        assertThat(webhookResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Integer webhookCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM children.esign_webhook_events WHERE tenant_id = ?",
                Integer.class,
                tenantId
        );
        assertThat(webhookCount).isNotNull();
        assertThat(webhookCount).isEqualTo(1);

        Integer failedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM children.esign_webhook_events WHERE tenant_id = ? AND signature_verification_status = 'FAILED'",
                Integer.class,
                tenantId
        );
        assertThat(failedCount).isNotNull();
        assertThat(failedCount).isEqualTo(1);

        Integer auditFailed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM guardian.audit_events WHERE tenant_id = ? AND action = ?",
                Integer.class,
                tenantId,
                "ESIGN_SIGNATURE_FAILED"
        );
        assertThat(auditFailed).isNotNull();
        assertThat(auditFailed).isGreaterThan(0);

        Integer outboxFailed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId,
                "esign.signature_failed"
        );
        assertThat(outboxFailed).isNotNull();
        assertThat(outboxFailed).isGreaterThan(0);

        Integer artifactCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM children.consent_signed_artifacts WHERE tenant_id = ?",
                Integer.class,
                tenantId
        );
        assertThat(artifactCount).isNotNull();
        assertThat(artifactCount).isZero();
        assertThat(esignRequestRepository.findById(esignResponse.esignRequestId()).orElseThrow().getStatus())
                .isEqualTo("CREATED");
    }

    @Test
    void webhookVerifiedStoresArtifactAndUpdatesConsent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        GuardianConsent consent = seedConsent(tenantId);

        CreateEsignRequestResponse esignResponse = createEsignRequest(tenantId, userId, consent, "k3");

        wireMock.stubFor(post(urlEqualTo("/evidence/artifacts"))
                .willReturn(okJson("{\"artifactRef\":\"artifacts/signed/abc123\"}")));

        String payload = buildWebhookPayload("evt-2", esignResponse.providerEnvelopeId(), "DOCUMENT_SIGNED", "signed-bytes");
        String signature = hmacSha256Hex("test-secret", payload.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Stub-Signature", signature);

        ResponseEntity<String> webhookResponse = restTemplate.exchange(
                "/esign/webhooks/STUB",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                String.class
        );

        assertThat(webhookResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer verifiedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM children.esign_webhook_events WHERE tenant_id = ? AND signature_verification_status = 'VERIFIED'",
                Integer.class,
                tenantId
        );
        assertThat(verifiedCount).isNotNull();
        assertThat(verifiedCount).isEqualTo(1);

        Integer artifactCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM children.consent_signed_artifacts WHERE tenant_id = ?",
                Integer.class,
                tenantId
        );
        assertThat(artifactCount).isNotNull();
        assertThat(artifactCount).isEqualTo(1);
        assertThat(consentRepository.findByTenantIdAndConsentId(tenantId, consent.getConsentId()).orElseThrow().getSignedArtifactId())
                .isNotNull();

        assertThat(esignRequestRepository.findById(esignResponse.esignRequestId()).orElseThrow().getStatus())
                .isEqualTo("SIGNED");

        Integer auditStored = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM guardian.audit_events WHERE tenant_id = ? AND action = ?",
                Integer.class,
                tenantId,
                "GUARDIAN_SIGNED_ARTIFACT_STORED"
        );
        assertThat(auditStored).isNotNull();
        assertThat(auditStored).isGreaterThan(0);

        Integer outboxStored = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId,
                "guardian.signed_artifact_stored"
        );
        assertThat(outboxStored).isNotNull();
        assertThat(outboxStored).isGreaterThan(0);
    }

    @Test
    void approvalRequiresSignedArtifact() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        GuardianConsent consent = seedConsent(tenantId);

        ApproveConsentRequest approveRequest = new ApproveConsentRequest("APPROVED", "ok");
        HttpHeaders headers = tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"));

        ResponseEntity<String> response = restTemplate.exchange(
                "/consents/" + consent.getConsentId() + "/approve",
                HttpMethod.POST,
                new HttpEntity<>(approveRequest, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        CreateEsignRequestResponse esignResponse = createEsignRequest(tenantId, userId, consent, "k4");
        wireMock.stubFor(post(urlEqualTo("/evidence/artifacts"))
                .willReturn(okJson("{\"artifactRef\":\"artifacts/signed/abc999\"}")));

        String payload = buildWebhookPayload("evt-3", esignResponse.providerEnvelopeId(), "DOCUMENT_SIGNED", "signed-bytes");
        String signature = hmacSha256Hex("test-secret", payload.getBytes(StandardCharsets.UTF_8));

        HttpHeaders webhookHeaders = new HttpHeaders();
        webhookHeaders.set("X-Tenant-ID", tenantId.toString());
        webhookHeaders.setContentType(MediaType.APPLICATION_JSON);
        webhookHeaders.set("X-Stub-Signature", signature);

        restTemplate.exchange(
                "/esign/webhooks/STUB",
                HttpMethod.POST,
                new HttpEntity<>(payload, webhookHeaders),
                String.class
        );

        ResponseEntity<String> approved = restTemplate.exchange(
                "/consents/" + consent.getConsentId() + "/approve",
                HttpMethod.POST,
                new HttpEntity<>(approveRequest, headers),
                String.class
        );

        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void webhookIsIdempotent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        GuardianConsent consent = seedConsent(tenantId);

        CreateEsignRequestResponse esignResponse = createEsignRequest(tenantId, userId, consent, "k5");
        wireMock.stubFor(post(urlEqualTo("/evidence/artifacts"))
                .willReturn(okJson("{\"artifactRef\":\"artifacts/signed/abc555\"}")));

        String payload = buildWebhookPayload("evt-4", esignResponse.providerEnvelopeId(), "DOCUMENT_SIGNED", "signed-bytes");
        String signature = hmacSha256Hex("test-secret", payload.getBytes(StandardCharsets.UTF_8));

        HttpHeaders webhookHeaders = new HttpHeaders();
        webhookHeaders.set("X-Tenant-ID", tenantId.toString());
        webhookHeaders.setContentType(MediaType.APPLICATION_JSON);
        webhookHeaders.set("X-Stub-Signature", signature);

        restTemplate.exchange(
                "/esign/webhooks/STUB",
                HttpMethod.POST,
                new HttpEntity<>(payload, webhookHeaders),
                String.class
        );
        restTemplate.exchange(
                "/esign/webhooks/STUB",
                HttpMethod.POST,
                new HttpEntity<>(payload, webhookHeaders),
                String.class
        );

        Integer webhookCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM children.esign_webhook_events WHERE tenant_id = ? AND provider_envelope_id = ? AND provider_event_id = ?",
                Integer.class,
                tenantId,
                esignResponse.providerEnvelopeId(),
                "evt-4"
        );
        assertThat(webhookCount).isNotNull();
        assertThat(webhookCount).isEqualTo(1);

        Integer artifactCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM children.consent_signed_artifacts WHERE tenant_id = ?",
                Integer.class,
                tenantId
        );
        assertThat(artifactCount).isNotNull();
        assertThat(artifactCount).isEqualTo(1);
    }

    private GuardianConsent seedConsent(UUID tenantId) {
        Child child = new Child();
        child.setTenantId(tenantId);
        child.setChildRef("CHILD-" + UUID.randomUUID());
        child.setFullName("Test Child");
        child.setDateOfBirth(LocalDate.now().minusYears(12));
        child.setCountry("US");
        child.setStatus("ACTIVE");
                Child savedChild = childRepository.save(child);
        Guardian guardian = new Guardian();
        guardian.setTenantId(tenantId);
        guardian.setChildId(savedChild.getChildId());
        guardian.setGuardianName("Test Guardian");
        guardian.setGuardianEmail("guardian@example.com");
        guardian.setGuardianPhone("+10000000000");
        guardian.setRelationship("PARENT");
        guardian.setStatus(ConsentStateMachine.GUARDIAN_VERIFIED);
        guardian.setVerifiedAt(Instant.now());
        guardian.setVerifiedBy(UUID.randomUUID());

        Guardian savedGuardian = guardianRepository.save(guardian);

        GuardianConsent consent = new GuardianConsent();
        consent.setTenantId(tenantId);
        consent.setChildId(savedChild.getChildId());
        consent.setGuardianId(savedGuardian.getGuardianId());
        consent.setPurposeKey("DATA_PROCESSING");
        consent.setConsentScope("FULL");
        consent.setStatus(ConsentStateMachine.STATUS_SUBMITTED);
        consent.setRequiresApproval(true);
        consent.setIdempotencyKey(UUID.randomUUID().toString());
        consent.setValidFrom(LocalDate.now());
        consent.setValidTo(LocalDate.now().plusYears(1));

                return consentRepository.save(consent);
    }

    private CreateEsignRequestResponse createEsignRequest(UUID tenantId, UUID userId, GuardianConsent consent, String key) throws Exception {
        CreateEsignRequestRequest request = new CreateEsignRequestRequest("STUB", "GUARDIAN_CONSENT", 1, "https://return.local");
        HttpHeaders headers = tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"));
        headers.set("X-Idempotency-Key", key);

        ResponseEntity<CreateEsignRequestResponse> response = restTemplate.exchange(
                "/consents/" + consent.getConsentId() + "/esign-requests",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                CreateEsignRequestResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
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

    private String buildWebhookPayload(String eventId, String envelopeId, String eventType, String content) throws Exception {
        String base64 = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> payload = Map.of(
                "eventId", eventId,
                "envelopeId", envelopeId,
                "eventType", eventType,
                "signedDocumentBase64", base64,
                "mime", "application/pdf",
                "filename", "guardian-consent.pdf"
        );
        return objectMapper.writeValueAsString(payload);
    }

    private String hmacSha256Hex(String secret, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmac = mac.doFinal(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hmac) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
