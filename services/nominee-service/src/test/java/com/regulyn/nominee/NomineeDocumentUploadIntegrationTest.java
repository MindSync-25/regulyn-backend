package com.regulyn.nominee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.nominee.dto.NomineeDocumentResponse;
import com.regulyn.nominee.entity.ClaimDocument;
import com.regulyn.nominee.entity.Nominee;
import com.regulyn.nominee.entity.NomineeClaim;
import com.regulyn.nominee.entity.NomineeDocument;
import com.regulyn.nominee.repository.ClaimDocumentRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
class NomineeDocumentUploadIntegrationTest {

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
    private ClaimDocumentRepository claimDocumentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("evidence.baseUrl", () -> wireMockServer.baseUrl());
        registry.add("nominee.documents.maxSizeBytes", () -> 10_485_760L);
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
    void multipartUploadStoresDocumentAndEvents() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Nominee nominee = createNominee(tenantId);

        byte[] bytes = "hello-world".getBytes(StandardCharsets.UTF_8);
        String expectedHash = sha256Hex(bytes);
        stubArtifactUpload("artifact-123", expectedHash);

        MockMultipartFile file = new MockMultipartFile("file", "id.txt", "text/plain", bytes);
        MockMultipartFile verificationStep = new MockMultipartFile("verificationStep", "", "text/plain", "IDENTITY_PROOF".getBytes(StandardCharsets.UTF_8));

        var result = mockMvc.perform(
                        multipart("/nominees/{id}/documents", nominee.getId())
                                .file(file)
                                .file(verificationStep)
                                .header("X-Tenant-ID", tenantId.toString())
                                .header("X-User-ID", userId.toString())
                )
                .andExpect(status().isOk())
                .andReturn();

        NomineeDocumentResponse response = objectMapper.readValue(result.getResponse().getContentAsByteArray(), NomineeDocumentResponse.class);

        NomineeDocument doc = nomineeDocumentRepository.findById(response.getNomineeDocumentId()).orElseThrow();
        assertThat(doc.getSha256Hash()).isEqualTo(expectedHash);
        assertThat(doc.getArtifactRef()).isEqualTo("artifact-123");
        assertThat(doc.getTenantId()).isEqualTo(tenantId);

        assertAuditAndOutbox(doc.getId());
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/evidence/artifacts")));
    }

    @Test
    void duplicateUploadReturnsExistingAndSkipsEvidenceCall() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Nominee nominee = createNominee(tenantId);

        byte[] bytes = "duplicate".getBytes(StandardCharsets.UTF_8);
        String expectedHash = sha256Hex(bytes);
        stubArtifactUpload("artifact-dup", expectedHash);

        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", bytes);
        MockMultipartFile verificationStep = new MockMultipartFile("verificationStep", "", "text/plain", "ADDRESS_PROOF".getBytes(StandardCharsets.UTF_8));

        var firstResult = mockMvc.perform(
                        multipart("/nominees/{id}/documents", nominee.getId())
                                .file(file)
                                .file(verificationStep)
                                .header("X-Tenant-ID", tenantId.toString())
                                .header("X-User-ID", userId.toString())
                )
                .andExpect(status().isOk())
                .andReturn();

        NomineeDocumentResponse firstResponse = objectMapper.readValue(firstResult.getResponse().getContentAsByteArray(), NomineeDocumentResponse.class);

        var secondResult = mockMvc.perform(
                        multipart("/nominees/{id}/documents", nominee.getId())
                                .file(file)
                                .file(verificationStep)
                                .header("X-Tenant-ID", tenantId.toString())
                                .header("X-User-ID", userId.toString())
                )
                .andExpect(status().isOk())
                .andReturn();

        NomineeDocumentResponse secondResponse = objectMapper.readValue(secondResult.getResponse().getContentAsByteArray(), NomineeDocumentResponse.class);

        assertThat(secondResponse.getNomineeDocumentId()).isEqualTo(firstResponse.getNomineeDocumentId());
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/evidence/artifacts")));
    }

    @Test
    void uploadWithClaimIdLinksClaimDocument() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Nominee nominee = createNominee(tenantId);
        NomineeClaim claim = createClaim(tenantId, nominee.getId());

        byte[] bytes = "claim-link".getBytes(StandardCharsets.UTF_8);
        String expectedHash = sha256Hex(bytes);
        stubArtifactUpload("artifact-claim", expectedHash);

        MockMultipartFile file = new MockMultipartFile("file", "claim.txt", "text/plain", bytes);
        MockMultipartFile verificationStep = new MockMultipartFile("verificationStep", "", "text/plain", "CLAIM_DOC".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile claimIdPart = new MockMultipartFile("claimId", "", "text/plain", claim.getId().toString().getBytes(StandardCharsets.UTF_8));

        var result = mockMvc.perform(
                        multipart("/nominees/{id}/documents", nominee.getId())
                                .file(file)
                                .file(verificationStep)
                                .file(claimIdPart)
                                .header("X-Tenant-ID", tenantId.toString())
                                .header("X-User-ID", userId.toString())
                )
                .andExpect(status().isOk())
                .andReturn();

        NomineeDocumentResponse response = objectMapper.readValue(result.getResponse().getContentAsByteArray(), NomineeDocumentResponse.class);

        List<ClaimDocument> claimDocs = claimDocumentRepository.findByClaimIdAndNomineeDocumentId(claim.getId(), response.getNomineeDocumentId());
        assertThat(claimDocs).hasSize(1);
        assertThat(claimDocs.get(0).getStorageUrl()).isEqualTo("artifact-claim");
    }

    @Test
    void refSubmissionStoresDocumentAndRequiresSha256() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Nominee nominee = createNominee(tenantId);

        String sha256 = sha256Hex("ref-doc".getBytes(StandardCharsets.UTF_8));
        String payload = """
            {
              "verificationStep": "IDENTITY_PROOF",
              "artifactRef": "artifact-ref-1",
              "sha256Hash": "%s",
              "filename": "proof.pdf",
              "contentType": "application/pdf",
              "sizeBytes": 1234,
              "notes": "ref upload"
            }
            """.formatted(sha256);

        var result = mockMvc.perform(
                        post("/nominees/{id}/documents/ref", nominee.getId())
                                .contentType("application/json")
                                .content(payload)
                                .header("X-Tenant-ID", tenantId.toString())
                                .header("X-User-ID", userId.toString())
                )
                .andExpect(status().isOk())
                .andReturn();

        NomineeDocumentResponse response = objectMapper.readValue(result.getResponse().getContentAsByteArray(), NomineeDocumentResponse.class);

        NomineeDocument doc = nomineeDocumentRepository.findById(response.getNomineeDocumentId()).orElseThrow();
        assertThat(doc.getSha256Hash()).isEqualTo(sha256);
        assertThat(doc.getArtifactRef()).isEqualTo("artifact-ref-1");
        assertThat(doc.getSourceType()).isEqualTo("PRESIGNED_REF");

        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/evidence/artifacts")));
    }

    private void stubArtifactUpload(String artifactRef, String sha256) {
        wireMockServer.stubFor(WireMock.post(urlEqualTo("/evidence/artifacts"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"artifactRef\":\"" + artifactRef + "\",\"sha256\":\"" + sha256 + "\"}")));
    }

    private Nominee createNominee(UUID tenantId) {
        Nominee nominee = new Nominee();
        nominee.setTenantId(tenantId);
        nominee.setDataPrincipalId(UUID.randomUUID());
        nominee.setNomineeName("Test Nominee");
        nominee.setNomineeContact("test@example.com");
        nominee.setStatus("REGISTERED");
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

    private void assertAuditAndOutbox(UUID nomineeDocumentId) {
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nominee.audit_events WHERE entity_type = ? AND entity_id = ? AND action IN ('NOMINEE_DOC_UPLOADED','NOMINEE_DOC_ARTIFACT_STORED')",
                Integer.class,
                "NOMINEE_DOCUMENT",
                nomineeDocumentId.toString()
        );
        assertThat(auditCount).isEqualTo(2);

        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nominee.outbox_events WHERE entity_type = ? AND entity_id = ? AND event_type IN ('nominee.doc_uploaded','nominee.doc_artifact_stored')",
                Integer.class,
                "NOMINEE_DOCUMENT",
                nomineeDocumentId.toString()
        );
        assertThat(outboxCount).isEqualTo(2);
    }

    private String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        return HexFormat.of().formatHex(hash);
    }
}
