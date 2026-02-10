package com.regulyn.ropa.retention;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.ropa.TestSecurityConfig;
import com.regulyn.ropa.api.dto.RetentionPolicyUpsertRequest;
import com.regulyn.ropa.model.RopaSystem;
import com.regulyn.ropa.model.RetentionBasis;
import com.regulyn.ropa.repository.RopaSystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
public class RetentionAuditOutboxWritesIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ropa_retention_audit_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.schemas", () -> "ropa");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "ropa");
        registry.add("audit.schema", () -> "ropa");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RopaSystemRepository systemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000500");
    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000600");

    private UUID systemId;

    @BeforeEach
    void setup() {
        systemRepository.deleteAll();

        RopaSystem system = new RopaSystem();
        system.setTenantId(tenantId);
        system.setSystemName("CRM");
        system.setSystemType(RopaSystem.SystemType.DATABASE);
        system.setLocation(RopaSystem.Location.INDIA);
        system.setCriticality(RopaSystem.Criticality.HIGH);
        systemRepository.save(system);
        systemId = system.getSystemId();
    }

    @Test
    void shouldWriteAuditAndOutbox() throws Exception {
        RetentionPolicyUpsertRequest request = new RetentionPolicyUpsertRequest();
        request.setRetentionDays(180);
        request.setRetentionBasis(RetentionBasis.CONTRACT);
        request.setRetentionNote("contract");
        request.setReviewRequired(true);

        mockMvc.perform(put("/retention/systems/{systemId}", systemId)
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "AUD-1")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.audit_events WHERE tenant_id = ? AND action = 'RETENTION_POLICY_CREATED'",
            Integer.class,
            tenantId
        );
        assertThat(auditCount).isNotNull();
        assertThat(auditCount).isGreaterThanOrEqualTo(1);

        Integer outboxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.outbox_events WHERE tenant_id = ? AND event_type = 'ropa.retention_policy_created'",
            Integer.class,
            tenantId
        );
        assertThat(outboxCount).isNotNull();
        assertThat(outboxCount).isGreaterThanOrEqualTo(1);

        String auditMetadata = jdbcTemplate.queryForObject(
            "SELECT metadata::text FROM ropa.audit_events WHERE tenant_id = ? AND action = 'RETENTION_POLICY_CREATED' ORDER BY occurred_at DESC LIMIT 1",
            String.class,
            tenantId
        );
        assertThat(auditMetadata).contains("idempotencyKey");

        String outboxPayload = jdbcTemplate.queryForObject(
            "SELECT payload::text FROM ropa.outbox_events WHERE tenant_id = ? AND event_type = 'ropa.retention_policy_created' ORDER BY occurred_at DESC LIMIT 1",
            String.class,
            tenantId
        );
        assertThat(outboxPayload).contains("payloadHash");
    }
}
