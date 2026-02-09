package com.regulyn.vendor.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.events.outbox.OutboxWriter;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc(addFilters = false)
public class VendorAccessTelemetryExportTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("vendor_access_export_test")
        .withUsername("test")
        .withPassword("test");

    private static final WireMockServer wireMockServer = new WireMockServer(options().dynamicPort());

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (!wireMockServer.isRunning()) {
            wireMockServer.start();
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.schemas", () -> "vendor");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "vendor");
        registry.add("audit.schema", () -> "vendor");
        registry.add("evidence.service.url", () -> wireMockServer.baseUrl());
        registry.add("internal.auth.token", () -> INTERNAL_TOKEN);
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxWriter outboxWriter;

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID VENDOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");

    @BeforeEach
    void cleanup() {
        wireMockServer.resetAll();
        jdbcTemplate.execute(
            "TRUNCATE " +
                "vendor.vendor_access_events, " +
                "vendor.vendor_access_exports, " +
                "vendor.audit_events, " +
                "vendor.outbox_events " +
            "RESTART IDENTITY CASCADE"
        );
    }

    @Test
    void requestIsIdempotent_andWritesAuditOutbox() throws Exception {
        assertFalse(Mockito.mockingDetails(outboxWriter).isMock());

        OffsetDateTime from = OffsetDateTime.parse("2025-08-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2025-08-02T00:00:00Z");

        String response = mockMvc.perform(post("/vendor/access-exports")
                .header("X-Tenant-ID", TENANT_A.toString())
                .header("X-Idempotency-Key", "key-1")
                .contentType("application/json")
                .content(exportRequestBody(VENDOR_ID, from, to, "CSV")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REQUESTED"))
            .andReturn().getResponse().getContentAsString();

        Map<String, Object> payload = objectMapper.readValue(response, Map.class);
        String exportId = payload.get("exportId").toString();

        Integer exportCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM vendor.vendor_access_exports WHERE tenant_id = ? AND idempotency_key = ?",
            Integer.class,
            TENANT_A,
            "key-1"
        );

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM vendor.audit_events WHERE action = 'VENDOR_ACCESS_EXPORT_REQUESTED'",
            Integer.class
        );

        Integer outboxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM vendor.outbox_events WHERE event_type = 'vendor_access.export_requested'",
            Integer.class
        );

        assertThat(exportCount).isEqualTo(1);
        assertThat(auditCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);

        String response2 = mockMvc.perform(post("/vendor/access-exports")
                .header("X-Tenant-ID", TENANT_A.toString())
                .header("X-Idempotency-Key", "key-1")
                .contentType("application/json")
                .content(exportRequestBody(VENDOR_ID, from, to, "CSV")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        Map<String, Object> payload2 = objectMapper.readValue(response2, Map.class);
        assertThat(payload2.get("exportId").toString()).isEqualTo(exportId);
    }

    @Test
    void processorCreatesArtifact_andUpdatesExportRow() throws Exception {
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/evidence/artifacts"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"artifactRef\":\"art-123\",\"sha256\":\"hash-123\"}")));

        OffsetDateTime from = OffsetDateTime.parse("2025-09-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2025-09-03T00:00:00Z");

        insertEvent(TENANT_A, VENDOR_ID, "user-a", "CRM", "READ", "ALLOWED", "corr-1", from.plusHours(1));
        insertEvent(TENANT_A, VENDOR_ID, "user-b", "CRM", "READ", "DENIED", "corr-2", from.plusHours(2));
        insertEvent(TENANT_A, VENDOR_ID, "user-c", "ERP", "WRITE", "ERROR", "corr-3", from.plusHours(3));

        String response = mockMvc.perform(post("/vendor/access-exports")
                .header("X-Tenant-ID", TENANT_A.toString())
                .header("X-Idempotency-Key", "key-2")
                .contentType("application/json")
                .content(exportRequestBody(VENDOR_ID, from, to, "CSV")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String exportId = objectMapper.readTree(response).get("exportId").asText();

        mockMvc.perform(post("/vendor/internal/access-exports/{exportId}/process", exportId)
            .header("X-Internal-Auth", INTERNAL_TOKEN)
            .header("X-Tenant-ID", TENANT_A.toString()))
            .andExpect(status().isOk());

        Map<String, Object> exportRow = jdbcTemplate.queryForMap(
            "SELECT status, artifact_ref, file_hash, total_events, allowed_events, denied_events, error_events FROM vendor.vendor_access_exports WHERE export_id = ?",
            UUID.fromString(exportId)
        );

        assertThat(exportRow.get("status")).isEqualTo("CREATED");
        assertThat(exportRow.get("artifact_ref")).isEqualTo("art-123");
        assertThat(exportRow.get("file_hash")).isNotNull();
        assertThat(((Number) exportRow.get("total_events")).longValue()).isEqualTo(3);
        assertThat(((Number) exportRow.get("allowed_events")).longValue()).isEqualTo(1);
        assertThat(((Number) exportRow.get("denied_events")).longValue()).isEqualTo(1);
        assertThat(((Number) exportRow.get("error_events")).longValue()).isEqualTo(1);

        Integer auditCreated = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM vendor.audit_events WHERE action = 'VENDOR_ACCESS_EXPORT_CREATED'",
            Integer.class
        );
        Integer auditStored = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM vendor.audit_events WHERE action = 'VENDOR_ACCESS_EVIDENCE_ARTIFACT_STORED'",
            Integer.class
        );
        Integer outboxCreated = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM vendor.outbox_events WHERE event_type = 'vendor_access.export_created'",
            Integer.class
        );
        Integer outboxStored = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM vendor.outbox_events WHERE event_type = 'vendor_access.evidence_artifact_stored'",
            Integer.class
        );

        assertThat(auditCreated).isEqualTo(1);
        assertThat(auditStored).isEqualTo(1);
        assertThat(outboxCreated).isEqualTo(1);
        assertThat(outboxStored).isEqualTo(1);

        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/evidence/artifacts")));
        String requestBody = wireMockServer.getAllServeEvents().get(0).getRequest().getBodyAsString();
        Map<String, Object> requestJson = objectMapper.readValue(requestBody, Map.class);
        String encodedBytes = requestJson.get("bytes").toString();
        String decoded = new String(java.util.Base64.getDecoder().decode(encodedBytes));
        assertThat(decoded).doesNotContain("rawPayloadRef");
    }

    @Test
    void processorIsRetrySafe() throws Exception {
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/evidence/artifacts"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"artifactRef\":\"art-456\",\"sha256\":\"hash-456\"}")));

        OffsetDateTime from = OffsetDateTime.parse("2025-10-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2025-10-02T00:00:00Z");

        insertEvent(TENANT_A, VENDOR_ID, "user-a", "CRM", "READ", "ALLOWED", "corr-10", from.plusHours(1));

        String response = mockMvc.perform(post("/vendor/access-exports")
                .header("X-Tenant-ID", TENANT_A.toString())
                .header("X-Idempotency-Key", "key-3")
                .contentType("application/json")
                .content(exportRequestBody(VENDOR_ID, from, to, "JSON")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String exportId = objectMapper.readTree(response).get("exportId").asText();

        mockMvc.perform(post("/vendor/internal/access-exports/{exportId}/process", exportId)
            .header("X-Internal-Auth", INTERNAL_TOKEN)
            .header("X-Tenant-ID", TENANT_A.toString()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/vendor/internal/access-exports/{exportId}/process", exportId)
            .header("X-Internal-Auth", INTERNAL_TOKEN)
            .header("X-Tenant-ID", TENANT_A.toString()))
            .andExpect(status().isOk());

        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/evidence/artifacts")));
    }

    @Test
    void tenantIsolation() throws Exception {
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/evidence/artifacts"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"artifactRef\":\"art-789\",\"sha256\":\"hash-789\"}")));

        OffsetDateTime from = OffsetDateTime.parse("2025-11-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2025-11-02T00:00:00Z");

        insertEvent(TENANT_A, VENDOR_ID, "user-a", "CRM", "READ", "ALLOWED", "corr-20", from.plusHours(1));
        insertEvent(TENANT_B, VENDOR_ID, "user-b", "CRM", "READ", "ALLOWED", "corr-21", from.plusHours(2));

        String response = mockMvc.perform(post("/vendor/access-exports")
                .header("X-Tenant-ID", TENANT_A.toString())
                .header("X-Idempotency-Key", "key-4")
                .contentType("application/json")
                .content(exportRequestBody(VENDOR_ID, from, to, "CSV")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String exportId = objectMapper.readTree(response).get("exportId").asText();

        mockMvc.perform(post("/vendor/internal/access-exports/{exportId}/process", exportId)
            .header("X-Internal-Auth", INTERNAL_TOKEN)
            .header("X-Tenant-ID", TENANT_A.toString()))
            .andExpect(status().isOk());

        Map<String, Object> exportRow = jdbcTemplate.queryForMap(
            "SELECT total_events FROM vendor.vendor_access_exports WHERE export_id = ?",
            UUID.fromString(exportId)
        );

        assertThat(((Number) exportRow.get("total_events")).longValue()).isEqualTo(1);
    }

    @Test
    void validationErrors() throws Exception {
        OffsetDateTime from = OffsetDateTime.parse("2025-12-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2025-12-01T00:00:00Z");

        mockMvc.perform(post("/vendor/access-exports")
                .header("X-Tenant-ID", TENANT_A.toString())
                .contentType("application/json")
                .content(exportRequestBody(VENDOR_ID, from, from.plusDays(1), "CSV")))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/vendor/access-exports")
                .header("X-Tenant-ID", TENANT_A.toString())
                .header("X-Idempotency-Key", "key-5")
                .contentType("application/json")
                .content(exportRequestBody(VENDOR_ID, from, to, "CSV")))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/vendor/access-exports")
                .header("X-Tenant-ID", TENANT_A.toString())
                .header("X-Idempotency-Key", "key-6")
                .contentType("application/json")
                .content(exportRequestBody(VENDOR_ID, from, from.plusDays(120), "CSV")))
            .andExpect(status().isBadRequest());
    }

    private String exportRequestBody(UUID vendorId, OffsetDateTime from, OffsetDateTime to, String format) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("vendorId", vendorId.toString());
        body.put("from", from.toString());
        body.put("to", to.toString());
        body.put("format", format);
        return objectMapper.writeValueAsString(body);
    }

    private void insertEvent(
        UUID tenantId,
        UUID vendorId,
        String subjectRef,
        String systemName,
        String accessType,
        String result,
        String correlationId,
        OffsetDateTime accessedAt
    ) {
        jdbcTemplate.update(
            "INSERT INTO vendor.vendor_access_events " +
                "(access_event_id, tenant_id, vendor_id, system_name, access_type, accessed_at, correlation_id, actor_type, result, raw_payload_hash, subject_ref, received_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(),
            tenantId,
            vendorId,
            systemName,
            accessType,
            accessedAt,
            correlationId,
            "SYSTEM",
            result,
            "hash-" + correlationId,
            subjectRef,
            accessedAt.plusMinutes(1)
        );
    }
}
