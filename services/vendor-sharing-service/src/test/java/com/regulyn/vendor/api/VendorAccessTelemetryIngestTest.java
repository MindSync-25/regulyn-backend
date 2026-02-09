package com.regulyn.vendor.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "test.outbox.mock=false")
@Testcontainers
@AutoConfigureMockMvc(addFilters = false)
public class VendorAccessTelemetryIngestTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("vendor_access_ingest_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.schemas", () -> "vendor");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "vendor");
        registry.add("audit.schema", () -> "vendor");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.regulyn.events.outbox.OutboxWriter outboxWriter;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute(
            "TRUNCATE " +
                "vendor.vendor_access_events, " +
                "vendor.audit_events, " +
                "vendor.outbox_events " +
            "RESTART IDENTITY CASCADE"
        );
    }

    @Test
    void shouldInsertEvent_andWriteAuditAndOutbox() throws Exception {
        assertFalse(Mockito.mockingDetails(outboxWriter).isMock());

        Map<String, Object> event = new HashMap<>();
        event.put("vendorId", UUID.randomUUID().toString());
        event.put("systemName", "CRM");
        event.put("accessType", "READ");
        event.put("accessedAt", OffsetDateTime.now().minusMinutes(5).toString());
        event.put("correlationId", "corr-1");
        event.put("actorType", "SYSTEM");
        event.put("result", "ALLOWED");
        event.put("rawPayloadRef", Map.of("key", "value"));

        Map<String, Object> request = Map.of("events", List.of(event));

        mockMvc.perform(post("/vendor/access-events/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-ID", TENANT_ID.toString())
                .header("X-User-ID", USER_ID.toString())
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.received").value(1))
            .andExpect(jsonPath("$.inserted").value(1))
            .andExpect(jsonPath("$.duplicates").value(0))
            .andExpect(jsonPath("$.failed").value(0));

        Integer eventCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM vendor.vendor_access_events",
            Integer.class
        );
        String auditSchema = resolveSchema("audit_events");
        String outboxSchema = resolveSchema("outbox_events");

        Integer auditCount = jdbcTemplate.queryForObject(
            String.format("SELECT COUNT(*) FROM %s.audit_events WHERE action = 'VENDOR_ACCESS_EVENT_INGESTED'", auditSchema),
            Integer.class
        );
        Integer outboxCount = jdbcTemplate.queryForObject(
            String.format("SELECT COUNT(*) FROM %s.outbox_events WHERE event_type = 'vendor_access.event_ingested'", outboxSchema),
            Integer.class
        );

        assertThat(eventCount).isEqualTo(1);
        assertThat(auditCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    void shouldIgnoreDuplicate_andWriteDuplicateAuditOutbox() throws Exception {
        String accessedAt = OffsetDateTime.now().minusHours(1).toString();
        String correlationId = "corr-dup";
        String vendorId = UUID.randomUUID().toString();

        Map<String, Object> event = new HashMap<>();
        event.put("vendorId", vendorId);
        event.put("systemName", "ERP");
        event.put("accessType", "READ");
        event.put("accessedAt", accessedAt);
        event.put("correlationId", correlationId);
        event.put("actorType", "SYSTEM");
        event.put("result", "ALLOWED");

        Map<String, Object> request = Map.of("events", List.of(event));

        mockMvc.perform(post("/vendor/access-events/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-ID", TENANT_ID.toString())
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inserted").value(1));

        mockMvc.perform(post("/vendor/access-events/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-ID", TENANT_ID.toString())
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.duplicates").value(1));

        Integer eventCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM vendor.vendor_access_events",
            Integer.class
        );
        String auditSchema = resolveSchema("audit_events");
        String outboxSchema = resolveSchema("outbox_events");

        Integer auditIngested = jdbcTemplate.queryForObject(
            String.format("SELECT COUNT(*) FROM %s.audit_events WHERE action = 'VENDOR_ACCESS_EVENT_INGESTED'", auditSchema),
            Integer.class
        );
        Integer auditDuplicate = jdbcTemplate.queryForObject(
            String.format("SELECT COUNT(*) FROM %s.audit_events WHERE action = 'VENDOR_ACCESS_EVENT_DUPLICATE_IGNORED'", auditSchema),
            Integer.class
        );
        Integer outboxIngested = jdbcTemplate.queryForObject(
            String.format("SELECT COUNT(*) FROM %s.outbox_events WHERE event_type = 'vendor_access.event_ingested'", outboxSchema),
            Integer.class
        );
        Integer outboxDuplicate = jdbcTemplate.queryForObject(
            String.format("SELECT COUNT(*) FROM %s.outbox_events WHERE event_type = 'vendor_access.event_duplicate_ignored'", outboxSchema),
            Integer.class
        );

        assertThat(eventCount).isEqualTo(1);
        assertThat(auditIngested).isEqualTo(1);
        assertThat(auditDuplicate).isEqualTo(1);
        assertThat(outboxIngested).isEqualTo(1);
        assertThat(outboxDuplicate).isEqualTo(1);
    }

    @Test
    void shouldAcceptMissingOptionalFields() throws Exception {
        Map<String, Object> event = new HashMap<>();
        event.put("vendorId", UUID.randomUUID().toString());
        event.put("systemName", "CRM");
        event.put("accessType", "READ");
        event.put("accessedAt", OffsetDateTime.now().minusMinutes(10).toString());
        event.put("correlationId", "corr-opt");
        event.put("actorType", "USER");
        event.put("result", "ALLOWED");

        Map<String, Object> request = Map.of("events", List.of(event));

        mockMvc.perform(post("/vendor/access-events/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-ID", TENANT_ID.toString())
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inserted").value(1))
            .andExpect(jsonPath("$.failed").value(0));

        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT subject_ref, data_categories, purpose_ref, purpose_version, actor_id, ip, user_agent, raw_payload_ref " +
                "FROM vendor.vendor_access_events WHERE correlation_id = 'corr-opt'"
        );

        assertThat(row.get("subject_ref")).isNull();
        assertThat(row.get("data_categories")).isNull();
        assertThat(row.get("purpose_ref")).isNull();
        assertThat(row.get("purpose_version")).isNull();
        assertThat(row.get("actor_id")).isNull();
        assertThat(row.get("ip")).isNull();
        assertThat(row.get("user_agent")).isNull();
        assertThat(row.get("raw_payload_ref")).isNull();
    }

    private String resolveSchema(String tableName) {
        List<String> schemas = jdbcTemplate.queryForList(
            "SELECT table_schema FROM information_schema.tables WHERE table_name = ? ORDER BY table_schema",
            String.class,
            tableName
        );
        if (schemas.contains("vendor")) {
            return "vendor";
        }
        if (!schemas.isEmpty()) {
            return schemas.get(0);
        }
        return "vendor";
    }
}
