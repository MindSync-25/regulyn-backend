package com.regulyn.nominee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.nominee.dto.NomineeDocumentResponse;
import com.regulyn.nominee.dto.VerificationGateResponse;
import com.regulyn.nominee.entity.Nominee;
import com.regulyn.nominee.entity.NomineeClaim;
import com.regulyn.nominee.entity.NomineeDocument;
import com.regulyn.nominee.repository.NomineeClaimRepository;
import com.regulyn.nominee.repository.NomineeDocumentRepository;
import com.regulyn.nominee.repository.NomineeRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
class NomineeRound2Part3IntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    static WireMockServer wireMockServer;

    static {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NomineeRepository nomineeRepository;

    @Autowired
    private NomineeClaimRepository nomineeClaimRepository;

    @Autowired
    private NomineeDocumentRepository nomineeDocumentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("evidence.baseUrl", () -> wireMockServer.baseUrl());
        registry.add("audit.schema", () -> "nominee");
    }

    @AfterAll
    static void teardownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void resetState() {
        wireMockServer.resetAll();
        jdbcTemplate.execute("TRUNCATE TABLE nominee.claim_documents, nominee.nominee_documents, nominee.nominee_claims, nominee.nominees, nominee.audit_events, nominee.outbox_events RESTART IDENTITY CASCADE");
    }

    @Test
    void verifyBlockedWhenDocsMissing() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Nominee nominee = createNominee(tenantId);

        String request = """
            {"method":"DOC_CHECK"}
            """;

        var result = mockMvc.perform(
                        post("/nominees/{id}/verify", nominee.getId())
                                .contentType("application/json")
                                .content(request)
                )
                .andExpect(status().isConflict())
                .andReturn();

        VerificationGateResponse response = objectMapper.readValue(result.getResponse().getContentAsByteArray(), VerificationGateResponse.class);
        assertThat(response.getMissingSteps()).isNotEmpty();
        assertThat(response.getError()).isEqualTo("MISSING_REQUIRED_DOCS");

        Nominee stored = nomineeRepository.findById(nominee.getId()).orElseThrow();
        assertThat(stored.getStatus()).isNotEqualTo("VERIFIED");

        Integer approvedEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nominee.audit_events WHERE action = 'NOMINEE_VERIFICATION_APPROVED' AND entity_id = ?",
                Integer.class,
                nominee.getId().toString()
        );
        assertThat(approvedEvents).isEqualTo(0);
    }

    @Test
    void verifySucceedsWhenDocsPresent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Nominee nominee = createNominee(tenantId);

        submitRefDoc(tenantId, nominee.getId(), "IDENTITY_PROOF", "artifact-1");
        submitRefDoc(tenantId, nominee.getId(), "ADDRESS_PROOF", "artifact-2");
        submitRefDoc(tenantId, nominee.getId(), "AUTHORIZATION_LETTER", "artifact-3");

        String request = """
            {"method":"DOC_CHECK"}
            """;

        mockMvc.perform(
                        post("/nominees/{id}/verify", nominee.getId())
                                .contentType("application/json")
                                .content(request)
                )
                .andExpect(status().isOk());

        Integer submittedEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nominee.audit_events WHERE action = 'NOMINEE_VERIFICATION_SUBMITTED' AND entity_id = ?",
                Integer.class,
                nominee.getId().toString()
        );
        Integer approvedEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nominee.audit_events WHERE action = 'NOMINEE_VERIFICATION_APPROVED' AND entity_id = ?",
                Integer.class,
                nominee.getId().toString()
        );
        Integer submittedOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nominee.outbox_events WHERE event_type = 'nominee.verification_submitted' AND entity_id = ?",
                Integer.class,
                nominee.getId().toString()
        );
        Integer approvedOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nominee.outbox_events WHERE event_type = 'nominee.verification_approved' AND entity_id = ?",
                Integer.class,
                nominee.getId().toString()
        );

        assertThat(submittedEvents).isEqualTo(1);
        assertThat(approvedEvents).isEqualTo(1);
        assertThat(submittedOutbox).isEqualTo(1);
        assertThat(approvedOutbox).isEqualTo(1);
    }

    @Test
    void verifySucceedsWithExceptionEvidence() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Nominee nominee = createNominee(tenantId);

        String sha256 = sha256Hex("exception".getBytes(StandardCharsets.UTF_8));
        String exceptionBody = """
            {
              "exceptionReason": "Missing docs",
              "approvedBy": "admin-user",
              "notes": "approved",
              "artifactRef": "artifact-ex",
              "sha256Hash": "%s",
              "filename": "exception.pdf",
              "contentType": "application/pdf",
              "sizeBytes": 111
            }
            """.formatted(sha256);

        mockMvc.perform(
                        post("/nominees/{id}/verify/exception", nominee.getId())
                                .contentType("application/json")
                                .content(exceptionBody)
                                .header("X-Tenant-ID", tenantId.toString())
                )
                .andExpect(status().isCreated());

        String request = """
            {"method":"MANUAL"}
            """;

        mockMvc.perform(
                        post("/nominees/{id}/verify", nominee.getId())
                                .contentType("application/json")
                                .content(request)
                )
                .andExpect(status().isOk());

        List<NomineeDocument> docs = nomineeDocumentRepository.findByTenantIdAndNomineeIdAndVerificationStep(
                tenantId, nominee.getId(), "VERIFICATION_EXCEPTION");
        assertThat(docs).hasSize(1);

        Integer exceptionEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nominee.audit_events WHERE action = 'NOMINEE_VERIFICATION_EXCEPTION_RECORDED' AND entity_id = ?",
                Integer.class,
                docs.get(0).getId().toString()
        );
        Integer exceptionOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nominee.outbox_events WHERE event_type = 'nominee.verification_exception_recorded' AND entity_id = ?",
                Integer.class,
                docs.get(0).getId().toString()
        );

        assertThat(exceptionEvents).isEqualTo(1);
        assertThat(exceptionOutbox).isEqualTo(1);
    }

    @Test
    void verifyRejectEmitsEvent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Nominee nominee = createNominee(tenantId);

        String request = """
            {
              "method": "DOC_CHECK",
              "notes": "insufficient evidence",
              "rejectionReason": "MISSING_DOCS"
            }
            """;

        mockMvc.perform(
                        post("/nominees/{id}/verify/reject", nominee.getId())
                                .contentType("application/json")
                                .content(request)
                                .header("X-User-ID", adminId.toString())
                )
                .andExpect(status().isOk());

        Nominee stored = nomineeRepository.findById(nominee.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo("REJECTED");

        Integer rejectEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nominee.audit_events WHERE action = 'NOMINEE_VERIFICATION_REJECTED' AND entity_id = ?",
                Integer.class,
                nominee.getId().toString()
        );
        Integer rejectOutbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nominee.outbox_events WHERE event_type = 'nominee.verification_rejected' AND entity_id = ?",
                Integer.class,
                nominee.getId().toString()
        );

        assertThat(rejectEvents).isEqualTo(1);
        assertThat(rejectOutbox).isEqualTo(1);
    }

    @Test
    void claimCloseCreatesBundleWithDocs() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Nominee nominee = createNominee(tenantId);
        NomineeClaim claim = createClaim(tenantId, nominee.getId());

        submitRefDoc(tenantId, nominee.getId(), "IDENTITY_PROOF", "artifact-claim-1", claim.getId());
        submitRefDoc(tenantId, nominee.getId(), "ADDRESS_PROOF", "artifact-claim-2", claim.getId());
        submitRefDoc(tenantId, nominee.getId(), "AUTHORIZATION_LETTER", "artifact-claim-3", claim.getId());

        stubFor(WireMock.post(urlEqualTo("/evidence"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"evidenceId\":\"" + UUID.randomUUID() + "\"}")));
        stubFor(WireMock.post(urlEqualTo("/evidence/bundles"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"bundleId\":\"" + UUID.randomUUID() + "\"}")));

        String closeBody = """
            {"closureNotes":"done"}
            """;

        mockMvc.perform(
                        post("/claims/{id}/close", claim.getId())
                                .contentType("application/json")
                                .content(closeBody)
                                .header("X-User-ID", UUID.randomUUID().toString())
                )
                .andExpect(status().isOk());

        NomineeClaim stored = nomineeClaimRepository.findById(claim.getId()).orElseThrow();
        assertThat(stored.getEvidenceId()).isNotNull();

        verify(postRequestedFor(urlEqualTo("/evidence"))
                .withRequestBody(matchingJsonPath("$.payload.documents"))
                .withRequestBody(matchingJsonPath("$.payload.verificationDecision")));
        verify(postRequestedFor(urlEqualTo("/evidence/bundles")));

        Integer bundleEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nominee.outbox_events WHERE event_type IN ('nominee.evidence_bundle_created','nominee.evidence_bundle_updated') AND entity_id = ?",
                Integer.class,
                claim.getId().toString()
        );
        assertThat(bundleEvents).isEqualTo(1);
    }

    private Nominee createNominee(UUID tenantId) {
        Nominee nominee = new Nominee();
        nominee.setTenantId(tenantId);
        nominee.setDataPrincipalId(UUID.randomUUID());
        nominee.setNomineeName("Test Nominee");
        nominee.setNomineeContact("test@example.com");
        nominee.setStatus("PENDING");
        return nomineeRepository.save(nominee);
    }

    private NomineeClaim createClaim(UUID tenantId, UUID nomineeId) {
        NomineeClaim claim = new NomineeClaim();
        claim.setTenantId(tenantId);
        claim.setNomineeId(nomineeId);
        claim.setClaimType("DSAR_SUBMISSION");
        claim.setStatus("SUBMITTED");
        return nomineeClaimRepository.save(claim);
    }

    private void submitRefDoc(UUID tenantId, UUID nomineeId, String step, String artifactRef) throws Exception {
        submitRefDoc(tenantId, nomineeId, step, artifactRef, null);
    }

    private void submitRefDoc(UUID tenantId, UUID nomineeId, String step, String artifactRef, UUID claimId) throws Exception {
        String sha256 = sha256Hex((artifactRef + step).getBytes(StandardCharsets.UTF_8));
        String body = """
            {
              "verificationStep": "%s",
              "artifactRef": "%s",
              "sha256Hash": "%s",
              "filename": "%s.pdf",
              "contentType": "application/pdf",
              "sizeBytes": 100,
              "claimId": %s
            }
            """.formatted(step, artifactRef, sha256, step.toLowerCase(), claimId != null ? "\"" + claimId + "\"" : "null");

        var result = mockMvc.perform(
                        post("/nominees/{id}/documents/ref", nomineeId)
                                .contentType("application/json")
                                .content(body)
                                .header("X-Tenant-ID", tenantId.toString())
                )
                .andExpect(status().isOk())
                .andReturn();

        NomineeDocumentResponse response = objectMapper.readValue(result.getResponse().getContentAsByteArray(), NomineeDocumentResponse.class);
        assertThat(response.getArtifactRef()).isEqualTo(artifactRef);
    }

    private String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder result = new StringBuilder();
        for (byte b : hash) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
