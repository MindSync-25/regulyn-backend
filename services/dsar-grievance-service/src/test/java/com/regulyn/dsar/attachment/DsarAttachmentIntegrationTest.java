package com.regulyn.dsar.attachment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.dsar.entity.DsarAttachmentEntity;
import com.regulyn.dsar.entity.DsarRequestEntity;
import com.regulyn.dsar.model.AttachmentReferenceRequest;
import com.regulyn.dsar.model.AttachmentResponse;
import com.regulyn.dsar.repository.DsarAttachmentRepository;
import com.regulyn.dsar.repository.DsarRequestRepository;
import com.regulyn.events.outbox.OutboxEventRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class DsarAttachmentIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    private static WireMockServer wireMockServer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("evidence.service.url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("dsar.sla.scheduler.enabled", () -> "false");
    }

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DsarRequestRepository dsarRequestRepository;

    @Autowired
    private DsarAttachmentRepository attachmentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

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
        context.setRequestId(UUID.randomUUID().toString());
        TenantContextHolder.setContext(context);

        wireMockServer.resetAll();
        outboxEventRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM dsar.audit_events");
        attachmentRepository.deleteAll();
        dsarRequestRepository.deleteAll();
    }

    @Test
    void uploadAttachmentStoresArtifactAndEvents() throws Exception {
        UUID dsarId = createDsar("RECEIVED");

        stubFor(post(urlEqualTo("/evidence"))
            .willReturn(okJson("{\"evidenceId\":\"artifact-1\",\"status\":\"STORED\"}")));

        byte[] content = "test-file-content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.txt", "text/plain", content);

        MvcResult result = mockMvc.perform(multipart("/dsar/{dsarId}/attachments/upload", dsarId)
                .file(file))
            .andExpect(status().isCreated())
            .andReturn();

        AttachmentResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(), AttachmentResponse.class);

        DsarAttachmentEntity saved = attachmentRepository.findById(response.getAttachmentId()).orElseThrow();
        assertEquals("UPLOAD", response.getType());
        assertEquals("UPLOAD", saved.getAttachmentType().name());
        assertEquals(1, saved.getVersion());
        assertEquals("test.txt", saved.getFilename());
        assertEquals("text/plain", saved.getContentType());
        assertEquals(content.length, saved.getSizeBytes());
        assertEquals(sha256Hex(content), saved.getSha256());
        assertEquals("evidence://artifact-1", saved.getArtifactRef());

        assertAuditEventCount("DSAR_ATTACHMENT_ADDED", 1);
        assertAuditEventCount("DSAR_ATTACHMENT_ARTIFACT_STORED", 1);

        assertEquals(1, outboxEventRepository.findAll().stream()
            .filter(e -> e.getEventType().equals("dsar.attachment_added")).count());
        assertEquals(1, outboxEventRepository.findAll().stream()
            .filter(e -> e.getEventType().equals("dsar.attachment_artifact_stored")).count());
    }

    @Test
    void referenceAttachmentStoresRecordedArtifactWithoutRawReferenceInEvents() throws Exception {
        UUID dsarId = createDsar("RECEIVED");

        stubFor(post(urlEqualTo("/evidence"))
            .willReturn(okJson("{\"evidenceId\":\"artifact-ref\",\"status\":\"STORED\"}")));

        AttachmentReferenceRequest request = new AttachmentReferenceRequest();
        request.setReferenceValue("https://example.com/doc/123");
        request.setReferenceHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        request.setFilename("ref.txt");
        request.setContentType("text/plain");

        MvcResult result = mockMvc.perform(post("/dsar/{dsarId}/attachments/reference", dsarId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        AttachmentResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(), AttachmentResponse.class);

        DsarAttachmentEntity saved = attachmentRepository.findById(response.getAttachmentId()).orElseThrow();
        assertEquals("REFERENCE", saved.getAttachmentType().name());
        assertEquals("https://example.com/doc/123", saved.getReferenceValue());
        assertEquals("evidence://artifact-ref", saved.getRecordedEvidenceArtifactRef());

        assertAuditEventCount("DSAR_ATTACHMENT_ADDED", 1);
        assertAuditEventCount("DSAR_ATTACHMENT_ARTIFACT_STORED", 1);

        String payload = jdbcTemplate.queryForObject(
            "SELECT payload::text FROM dsar.outbox_events WHERE event_type = 'dsar.attachment_added' AND entity_id = ?",
            String.class, response.getAttachmentId().toString());
        assertNotNull(payload);
        assertFalse(payload.contains("referenceValue"));
        assertFalse(payload.contains("https://example.com/doc/123"));
    }

    @Test
    void idempotencyPreventsDuplicateAttachments() throws Exception {
        UUID dsarId = createDsar("RECEIVED");

        stubFor(post(urlEqualTo("/evidence"))
            .willReturn(okJson("{\"evidenceId\":\"artifact-idem\",\"status\":\"STORED\"}")));

        byte[] content = "idempotent".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
            "file", "idem.txt", "text/plain", content);

        MvcResult first = mockMvc.perform(multipart("/dsar/{dsarId}/attachments/upload", dsarId)
                .file(file)
                .header("X-Idempotency-Key", "idem-key"))
            .andExpect(status().isCreated())
            .andReturn();

        MvcResult second = mockMvc.perform(multipart("/dsar/{dsarId}/attachments/upload", dsarId)
                .file(file)
                .header("X-Idempotency-Key", "idem-key"))
            .andExpect(status().isOk())
            .andReturn();

        AttachmentResponse firstResponse = objectMapper.readValue(
            first.getResponse().getContentAsString(), AttachmentResponse.class);
        AttachmentResponse secondResponse = objectMapper.readValue(
            second.getResponse().getContentAsString(), AttachmentResponse.class);

        assertEquals(firstResponse.getAttachmentId(), secondResponse.getAttachmentId());
        assertEquals(1, attachmentRepository.findByTenantIdAndDsarIdOrderByCreatedAtAsc(tenantId, dsarId).size());

        verify(1, postRequestedFor(urlEqualTo("/evidence")));
    }

    @Test
    void evidenceFailureRollsBackAttachmentCreate() throws Exception {
        UUID dsarId = createDsar("RECEIVED");

        stubFor(post(urlEqualTo("/evidence"))
            .willReturn(serverError()));

        byte[] content = "fail".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
            "file", "fail.txt", "text/plain", content);

        mockMvc.perform(multipart("/dsar/{dsarId}/attachments/upload", dsarId)
                .file(file))
            .andExpect(status().isServiceUnavailable());

        assertEquals(0, attachmentRepository.findByTenantIdAndDsarIdOrderByCreatedAtAsc(tenantId, dsarId).size());
        assertAuditEventCount("DSAR_ATTACHMENT_ADDED", 0);
        assertAuditEventCount("DSAR_ATTACHMENT_ARTIFACT_STORED", 0);
        assertEquals(0, outboxEventRepository.count());
    }

    @Test
    void closedDsarCannotAddAttachment() throws Exception {
        UUID dsarId = createDsar("CLOSED");

        byte[] content = "closed".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
            "file", "closed.txt", "text/plain", content);

        mockMvc.perform(multipart("/dsar/{dsarId}/attachments/upload", dsarId)
                .file(file))
            .andExpect(status().isConflict());

        assertEquals(0, attachmentRepository.findByTenantIdAndDsarIdOrderByCreatedAtAsc(tenantId, dsarId).size());
    }

    private UUID createDsar(String status) {
        DsarRequestEntity entity = new DsarRequestEntity();
        entity.setTenantId(tenantId);
        entity.setRequestId(UUID.randomUUID().toString());
        entity.setRequestType("ACCESS");
        entity.setStatus(status);
        entity.setRequesterEmail("test@example.com");
        entity.setDataPrincipalId(UUID.randomUUID());
        entity.setCreatedBy(userId);
        if ("CLOSED".equals(status)) {
            entity.setClosedAt(Instant.now());
        }
        return dsarRequestRepository.saveAndFlush(entity).getRequestIdPk();
    }

    private void assertAuditEventCount(String action, int expected) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dsar.audit_events WHERE action = ?",
            Integer.class, action);
        assertNotNull(count);
        assertEquals(expected, count.intValue());
    }

    private String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}