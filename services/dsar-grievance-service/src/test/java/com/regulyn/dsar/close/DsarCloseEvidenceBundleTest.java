/*
 * Postgres is real via Testcontainers; only evidence-reporting-service HTTP is stubbed via WireMock.
 * No internal close logic is mocked.
 */
package com.regulyn.dsar.close;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.dsar.entity.*;
import com.regulyn.dsar.model.AttachmentReferenceRequest;
import com.regulyn.dsar.model.CloseDsarRequest;
import com.regulyn.dsar.repository.*;
import com.regulyn.dsar.service.AttachmentService;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class DsarCloseEvidenceBundleTest {

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
        configureFor("localhost", wireMockServer.port());
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
    private DsarStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private DsarAttachmentRepository attachmentRepository;

    @Autowired
    private DsarEscalationTaskRepository escalationTaskRepository;

    @Autowired
    private DsarEvidenceBundleRefRepository bundleRefRepository;

    @Autowired
    private AttachmentService attachmentService;

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
        escalationTaskRepository.deleteAll();
        attachmentRepository.deleteAll();
        statusHistoryRepository.deleteAll();
        bundleRefRepository.deleteAll();
        dsarRequestRepository.deleteAll();
    }

    @Test
    void bundleIncludesAttachmentsAndEscalations() throws Exception {
        UUID dsarId = createDsar("COMPLETED");
        addStatusHistory(dsarId, "IN_REVIEW", "COMPLETED", "done");

        stubEvidenceSequence();

        MockMultipartFile file = new MockMultipartFile(
            "file", "test.txt", "text/plain", "file-content".getBytes(StandardCharsets.UTF_8));
        attachmentService.addUploadAttachment(tenantId, userId, dsarId, null, file);

        AttachmentReferenceRequest referenceRequest = new AttachmentReferenceRequest();
        referenceRequest.setReferenceValue("https://example.com/doc/123");
        referenceRequest.setReferenceHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        referenceRequest.setFilename("ref.txt");
        referenceRequest.setContentType("text/plain");
        attachmentService.addReferenceAttachment(tenantId, userId, dsarId, null, referenceRequest);

        addEscalation(dsarId);

        stubFor(post(urlEqualTo("/bundles"))
            .willReturn(okJson("{\"bundleId\":\"11111111-1111-1111-1111-111111111111\",\"bundleHash\":\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",\"status\":\"CREATED\"}")));

        CloseDsarRequest closeRequest = new CloseDsarRequest();
        closeRequest.setClosureNotes("close-notes");

        MvcResult closeResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/dsar/{dsarId}/close", dsarId)
                .with(withTenantContext())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(closeRequest)))
            .andReturn();
        assertEquals(200, closeResult.getResponse().getStatus(), closeResult.getResponse().getContentAsString());

        List<com.github.tomakehurst.wiremock.verification.LoggedRequest> bundleCalls =
            wireMockServer.findAll(postRequestedFor(urlEqualTo("/bundles")));
        assertEquals(1, bundleCalls.size());

        String body = bundleCalls.get(0).getBodyAsString();
        assertFalse(body.contains("referenceValue"));
        assertFalse(body.contains("https://example.com/doc/123"));

        JsonNode root = objectMapper.readTree(body);
        JsonNode metadata = root.get("metadata");
        assertNotNull(metadata);
        assertTrue(metadata.get("attachments").isArray());
        assertTrue(metadata.get("escalations").isArray());

        boolean hasUpload = false;
        boolean hasReference = false;
        for (JsonNode attachment : metadata.get("attachments")) {
            String type = attachment.get("type").asText();
            if ("UPLOAD".equals(type)) {
                hasUpload = attachment.hasNonNull("artifactRef") && attachment.hasNonNull("sha256");
            }
            if ("REFERENCE".equals(type)) {
                hasReference = attachment.hasNonNull("recordedEvidenceArtifactRef")
                    && attachment.has("referenceHash");
            }
        }
        assertTrue(hasUpload);
        assertTrue(hasReference);

        JsonNode escalation = metadata.get("escalations").get(0);
        assertEquals("D60", escalation.get("threshold").asText());
        assertEquals("req-1", escalation.get("notificationRequestId").asText());
        assertEquals("msg-1", escalation.get("providerMessageId").asText());

        assertAuditEventCount("DSAR_EVIDENCE_BUNDLE_UPDATED", 1);
        assertAuditEventCount("DSAR_EVIDENCE_BUNDLE_REF_CREATED", 1);
        assertOutboxEventCount("dsar.evidence_bundle_updated", 1);
        assertOutboxEventCount("dsar.evidence_bundle_ref_created", 1);
    }

    @Test
    void bundleIdempotencyOnRetry() throws Exception {
        UUID dsarId = createDsar("COMPLETED");
        stubEvidenceSequence();
        stubFor(post(urlEqualTo("/bundles"))
            .willReturn(okJson("{\"bundleId\":\"11111111-1111-1111-1111-111111111111\",\"bundleHash\":\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",\"status\":\"CREATED\"}")));

        CloseDsarRequest closeRequest = new CloseDsarRequest();
        closeRequest.setClosureNotes("close-notes");

        MvcResult firstClose = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/dsar/{dsarId}/close", dsarId)
                .with(withTenantContext())
                .header("X-Idempotency-Key", "close-1")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(closeRequest)))
            .andReturn();
        assertEquals(200, firstClose.getResponse().getStatus(), firstClose.getResponse().getContentAsString());

        MvcResult secondClose = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/dsar/{dsarId}/close", dsarId)
                .with(withTenantContext())
                .header("X-Idempotency-Key", "close-1")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(closeRequest)))
            .andReturn();
        assertEquals(200, secondClose.getResponse().getStatus(), secondClose.getResponse().getContentAsString());

        assertEquals(1, bundleRefRepository.count());
        assertEquals(1, wireMockServer.findAll(postRequestedFor(urlEqualTo("/bundles"))).size());
    }

    @Test
    void evidenceFailureFailsClose() throws Exception {
        UUID dsarId = createDsar("COMPLETED");

        stubEvidenceSequence();
        stubFor(post(urlEqualTo("/bundles"))
            .willReturn(serverError()));

        CloseDsarRequest closeRequest = new CloseDsarRequest();
        closeRequest.setClosureNotes("close-notes");

        MvcResult closeResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/dsar/{dsarId}/close", dsarId)
                .with(withTenantContext())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(closeRequest)))
            .andReturn();
        assertTrue(closeResult.getResponse().getStatus() >= 500,
            closeResult.getResponse().getContentAsString());

        DsarRequestEntity reloaded = dsarRequestRepository
            .findByRequestIdPkAndTenantId(dsarId, tenantId)
            .orElseThrow();
        assertNotEquals("CLOSED", reloaded.getStatus());
        assertNull(reloaded.getCloseEvidenceBundleId());
        assertEquals(0, bundleRefRepository.count());
    }

    private UUID createDsar(String status) {
        DsarRequestEntity entity = new DsarRequestEntity();
        entity.setTenantId(tenantId);
        entity.setRequestId(UUID.randomUUID().toString());
        entity.setRequestType("ACCESS");
        entity.setStatus(status);
        entity.setRequesterEmail("requester@example.com");
        entity.setCreatedBy(userId);
        entity.setCreatedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        entity.setUpdatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        entity.setDueAt(Instant.now().plus(80, ChronoUnit.DAYS));
        entity.setRequiresApproval(true);
        entity.setApprovedBy(userId);
        entity.setApprovedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        return dsarRequestRepository.saveAndFlush(entity).getRequestIdPk();
    }

    private void addStatusHistory(UUID dsarId, String from, String to, String reason) {
        DsarStatusHistory history = new DsarStatusHistory();
        history.setTenantId(tenantId);
        history.setDsarId(dsarId);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setChangedBy(userId);
        history.setReason(reason);
        statusHistoryRepository.save(history);
    }

    private void addEscalation(UUID dsarId) {
        DsarEscalationTaskEntity task = new DsarEscalationTaskEntity();
        task.setTenantId(tenantId);
        task.setDsarId(dsarId);
        task.setThreshold(EscalationThreshold.D60);
        task.setThresholdDays(60);
        task.setDueAt(Instant.now().minus(1, ChronoUnit.DAYS));
        task.setReachedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        task.setStatus(EscalationStatus.NOTIFICATION_SENT);
        task.setNotificationRequestId("req-1");
        task.setProviderMessageId("msg-1");
        escalationTaskRepository.save(task);
    }

    private void stubEvidenceSequence() {
        stubFor(post(urlEqualTo("/evidence"))
            .inScenario("evidence")
            .whenScenarioStateIs(STARTED)
            .willReturn(okJson("{\"evidenceId\":\"11111111-1111-1111-1111-111111111112\",\"status\":\"STORED\"}"))
            .willSetStateTo("SECOND"));

        stubFor(post(urlEqualTo("/evidence"))
            .inScenario("evidence")
            .whenScenarioStateIs("SECOND")
            .willReturn(okJson("{\"evidenceId\":\"11111111-1111-1111-1111-111111111113\",\"status\":\"STORED\"}"))
            .willSetStateTo("THIRD"));

        stubFor(post(urlEqualTo("/evidence"))
            .inScenario("evidence")
            .whenScenarioStateIs("THIRD")
            .willReturn(okJson("{\"evidenceId\":\"11111111-1111-1111-1111-111111111114\",\"status\":\"STORED\"}")));
    }

    private void assertAuditEventCount(String action, int expected) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dsar.audit_events WHERE action = ?",
            Integer.class, action);
        assertNotNull(count);
        assertEquals(expected, count.intValue());
    }

    private void assertOutboxEventCount(String eventType, int expected) {
        long count = outboxEventRepository.findAll().stream()
            .filter(event -> eventType.equals(event.getEventType()))
            .count();
        assertEquals(expected, count);
    }

    private RequestPostProcessor withTenantContext() {
        return request -> {
            TenantContext context = new TenantContext();
            context.setTenantId(tenantId);
            context.setUserId(userId);
            context.setRequestId(UUID.randomUUID().toString());
            TenantContextHolder.setContext(context);
            return request;
        };
    }
}
