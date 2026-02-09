package com.regulyn.vendor.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
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

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc(addFilters = false)
public class VendorAccessTelemetryQueryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("vendor_access_query_test")
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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID VENDOR_V1 = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID VENDOR_V2 = UUID.fromString("00000000-0000-0000-0000-000000000202");

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute(
            "TRUNCATE vendor.vendor_access_events RESTART IDENTITY CASCADE"
        );
    }

    @Test
    void filterByVendorAndTimeRange() throws Exception {
        OffsetDateTime from = OffsetDateTime.parse("2025-01-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2025-01-10T00:00:00Z");

        insertEvent(TENANT_A, VENDOR_V1, "user-a", "CRM", "READ", "ALLOWED", "corr-1", OffsetDateTime.parse("2025-01-02T00:00:00Z"));
        insertEvent(TENANT_A, VENDOR_V1, "user-b", "CRM", "READ", "DENIED", "corr-2", OffsetDateTime.parse("2025-01-03T00:00:00Z"));
        insertEvent(TENANT_A, VENDOR_V2, "user-c", "ERP", "READ", "ALLOWED", "corr-3", OffsetDateTime.parse("2025-01-02T00:00:00Z"));

        mockMvc.perform(get("/vendor/access-events")
                .header("X-Tenant-ID", TENANT_A.toString())
                .param("vendorId", VENDOR_V1.toString())
                .param("from", from.toString())
                .param("to", to.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.items", Matchers.hasSize(2)))
            .andExpect(jsonPath("$.items[*].vendorId", Matchers.everyItem(Matchers.is(VENDOR_V1.toString()))))
            .andExpect(jsonPath("$.items[*].rawPayloadRef").doesNotExist());
    }

    @Test
    void filterBySubjectRef() throws Exception {
        OffsetDateTime from = OffsetDateTime.parse("2025-02-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2025-02-10T00:00:00Z");

        insertEvent(TENANT_A, VENDOR_V1, "user@example.com", "CRM", "READ", "ALLOWED", "corr-10", OffsetDateTime.parse("2025-02-03T00:00:00Z"));
        insertEvent(TENANT_A, VENDOR_V1, "other@example.com", "CRM", "READ", "ALLOWED", "corr-11", OffsetDateTime.parse("2025-02-04T00:00:00Z"));

        mockMvc.perform(get("/vendor/access-events")
                .header("X-Tenant-ID", TENANT_A.toString())
                .param("subjectRef", "user@example.com")
                .param("from", from.toString())
                .param("to", to.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].subjectRef").value("user@example.com"));
    }

    @Test
    void filters_system_accessType_result() throws Exception {
        OffsetDateTime from = OffsetDateTime.parse("2025-03-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2025-03-10T00:00:00Z");

        insertEvent(TENANT_A, VENDOR_V1, "user-a", "Salesforce", "READ", "DENIED", "corr-20", OffsetDateTime.parse("2025-03-02T00:00:00Z"));
        insertEvent(TENANT_A, VENDOR_V1, "user-a", "Salesforce", "READ", "ALLOWED", "corr-21", OffsetDateTime.parse("2025-03-03T00:00:00Z"));
        insertEvent(TENANT_A, VENDOR_V1, "user-a", "ERP", "READ", "DENIED", "corr-22", OffsetDateTime.parse("2025-03-04T00:00:00Z"));

        mockMvc.perform(get("/vendor/access-events")
                .header("X-Tenant-ID", TENANT_A.toString())
                .param("vendorId", VENDOR_V1.toString())
                .param("from", from.toString())
                .param("to", to.toString())
                .param("systemName", "Salesforce")
                .param("accessType", "READ")
                .param("result", "DENIED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].systemName").value("Salesforce"))
            .andExpect(jsonPath("$.items[0].accessType").value("READ"))
            .andExpect(jsonPath("$.items[0].result").value("DENIED"));
    }

    @Test
    void tenantIsolation_forQueryAndSummary() throws Exception {
        OffsetDateTime from = OffsetDateTime.parse("2025-04-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2025-04-10T00:00:00Z");

        insertEvent(TENANT_A, VENDOR_V1, "user-a", "CRM", "READ", "ALLOWED", "corr-30", OffsetDateTime.parse("2025-04-02T00:00:00Z"));
        insertEvent(TENANT_B, VENDOR_V1, "user-a", "CRM", "READ", "ALLOWED", "corr-31", OffsetDateTime.parse("2025-04-03T00:00:00Z"));

        mockMvc.perform(get("/vendor/access-events")
                .header("X-Tenant-ID", TENANT_A.toString())
                .param("vendorId", VENDOR_V1.toString())
                .param("from", from.toString())
                .param("to", to.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/vendor/access-events/vendors/{vendorId}/summary", VENDOR_V1)
                .header("X-Tenant-ID", TENANT_A.toString())
                .param("from", from.toString())
                .param("to", to.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void paginationAndSorting() throws Exception {
        OffsetDateTime start = OffsetDateTime.parse("2025-05-01T00:00:00Z");
        for (int i = 0; i < 65; i++) {
            insertEvent(TENANT_A, VENDOR_V1, "user-" + i, "CRM", "READ", "ALLOWED", "corr-" + i, start.plusMinutes(i));
        }

        String responsePage0 = mockMvc.perform(get("/vendor/access-events")
                .header("X-Tenant-ID", TENANT_A.toString())
                .param("vendorId", VENDOR_V1.toString())
                .param("from", start.toString())
                .param("to", start.plusHours(2).toString())
                .param("page", "0")
                .param("size", "50")
                .param("sort", "accessedAt,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", Matchers.hasSize(50)))
            .andReturn().getResponse().getContentAsString();

        String responsePage1 = mockMvc.perform(get("/vendor/access-events")
                .header("X-Tenant-ID", TENANT_A.toString())
                .param("vendorId", VENDOR_V1.toString())
                .param("from", start.toString())
                .param("to", start.plusHours(2).toString())
                .param("page", "1")
                .param("size", "50")
                .param("sort", "accessedAt,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", Matchers.hasSize(15)))
            .andReturn().getResponse().getContentAsString();

        Map<String, Object> page0 = objectMapper.readValue(responsePage0, Map.class);
        Map<String, Object> page1 = objectMapper.readValue(responsePage1, Map.class);

        List<Map<String, Object>> items0 = (List<Map<String, Object>>) page0.get("items");
        List<Map<String, Object>> items1 = (List<Map<String, Object>>) page1.get("items");

        Set<String> ids = new HashSet<>();
        items0.forEach(item -> ids.add(item.get("accessEventId").toString()));
        items1.forEach(item -> ids.add(item.get("accessEventId").toString()));

        assertThat(ids).hasSize(65);
    }

    @Test
    void summaryCountsCorrect() throws Exception {
        OffsetDateTime from = OffsetDateTime.parse("2025-06-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2025-06-10T00:00:00Z");

        insertEvent(TENANT_A, VENDOR_V1, "user-a", "CRM", "READ", "ALLOWED", "corr-40", OffsetDateTime.parse("2025-06-02T00:00:00Z"));
        insertEvent(TENANT_A, VENDOR_V1, "user-a", "CRM", "READ", "DENIED", "corr-41", OffsetDateTime.parse("2025-06-03T00:00:00Z"));
        insertEvent(TENANT_A, VENDOR_V1, "user-a", "ERP", "WRITE", "ERROR", "corr-42", OffsetDateTime.parse("2025-06-04T00:00:00Z"));

        mockMvc.perform(get("/vendor/access-events/vendors/{vendorId}/summary", VENDOR_V1)
                .header("X-Tenant-ID", TENANT_A.toString())
                .param("from", from.toString())
                .param("to", to.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.allowed").value(1))
            .andExpect(jsonPath("$.denied").value(1))
            .andExpect(jsonPath("$.error").value(1))
            .andExpect(jsonPath("$.bySystem", Matchers.hasSize(2)))
            .andExpect(jsonPath("$.byAccessType", Matchers.hasSize(2)))
            .andExpect(jsonPath("$.byResult", Matchers.hasSize(3)));
    }

    @Test
    void validationErrors() throws Exception {
        OffsetDateTime from = OffsetDateTime.parse("2025-07-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2025-07-01T00:00:00Z");

        mockMvc.perform(get("/vendor/access-events")
                .header("X-Tenant-ID", TENANT_A.toString())
                .param("from", from.toString())
                .param("to", from.plusDays(1).toString()))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/vendor/access-events")
                .header("X-Tenant-ID", TENANT_A.toString())
                .param("vendorId", VENDOR_V1.toString())
                .param("from", from.toString())
                .param("to", to.toString()))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/vendor/access-events")
                .header("X-Tenant-ID", TENANT_A.toString())
                .param("vendorId", VENDOR_V1.toString())
                .param("from", from.toString())
                .param("to", from.plusDays(1).toString())
                .param("size", "500"))
            .andExpect(status().isBadRequest());
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
