package com.regulyn.ropa.crossborder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.ropa.TestSecurityConfig;
import com.regulyn.ropa.api.dto.CrossBorderTransferUpsertRequest;
import com.regulyn.ropa.model.RopaActivityVersion;
import com.regulyn.ropa.model.RopaDataCategory;
import com.regulyn.ropa.model.RopaSystem;
import com.regulyn.ropa.model.TransferFrequency;
import com.regulyn.ropa.model.TransferMechanism;
import com.regulyn.ropa.repository.CrossBorderTransferRepository;
import com.regulyn.ropa.repository.RopaActivityVersionRepository;
import com.regulyn.ropa.repository.RopaDataCategoryRepository;
import com.regulyn.ropa.repository.RopaSystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
public class CrossBorderAuditOutboxWritesTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ropa_cross_border_audit_test")
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
    private RopaActivityVersionRepository activityVersionRepository;

    @Autowired
    private RopaDataCategoryRepository dataCategoryRepository;

    @Autowired
    private CrossBorderTransferRepository transferRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000003100");
    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000003200");

    private UUID systemId;
    private UUID activityId;
    private UUID dataCategoryId;

    @BeforeEach
    void setup() {
        transferRepository.deleteAll();
        activityVersionRepository.deleteAll();
        systemRepository.deleteAll();
        dataCategoryRepository.deleteAll();

        RopaSystem system = new RopaSystem();
        system.setTenantId(tenantId);
        system.setSystemName("CRM");
        system.setSystemType(RopaSystem.SystemType.DATABASE);
        system.setLocation(RopaSystem.Location.INDIA);
        system.setCriticality(RopaSystem.Criticality.HIGH);
        systemRepository.save(system);
        systemId = system.getSystemId();

        RopaActivityVersion activity = new RopaActivityVersion();
        activityId = UUID.randomUUID();
        activity.setTenantId(tenantId);
        activity.setActivityId(activityId);
        activity.setVersionNumber(1);
        activity.setStatus(RopaActivityVersion.Status.PUBLISHED);
        activity.setActivityName("Onboarding");
        activity.setPurpose("KYC");
        activity.setLawfulBasis(RopaActivityVersion.LawfulBasis.CONTRACT);
        activity.setDataPrincipalType(RopaActivityVersion.DataPrincipalType.CUSTOMER);
        activity.setRiskLevel(RopaActivityVersion.RiskLevel.MED);
        activityVersionRepository.save(activity);

        RopaDataCategory category = new RopaDataCategory();
        category.setTenantId(tenantId);
        category.setCategoryKey(RopaDataCategory.CategoryKey.PII);
        category.setLabel("PII");
        category.setSensitive(true);
        dataCategoryRepository.save(category);
        dataCategoryId = category.getDataCategoryId();
    }

    @Test
    void shouldWriteAuditAndOutboxEvents() throws Exception {
        UUID vendorId = UUID.randomUUID();

        CrossBorderTransferUpsertRequest request = new CrossBorderTransferUpsertRequest();
        request.setSystemId(systemId);
        request.setActivityId(activityId);
        request.setVendorId(vendorId);
        request.setSourceRegion("INDIA");
        request.setDestinationRegion("US");
        request.setTransferMechanism(TransferMechanism.SCC);
        request.setLegalBasis("DPDP_S16");
        request.setFrequency(TransferFrequency.CONTINUOUS);
        request.setStartedAt(Instant.parse("2026-02-01T00:00:00Z"));
        request.setDataCategoryIds(List.of(dataCategoryId));
        request.setPurposeVersionIds(List.of(UUID.randomUUID()));

        mockMvc.perform(put("/cross-border/transfers")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "AUD-1")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        Integer auditRegistered = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.audit_events WHERE tenant_id = ? AND action = 'CROSS_BORDER_TRANSFER_REGISTERED'",
            Integer.class,
            tenantId
        );
        Integer outboxRegistered = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.outbox_events WHERE tenant_id = ? AND event_type = 'ropa.cross_border_transfer_registered'",
            Integer.class,
            tenantId
        );
        assertThat(auditRegistered).isNotNull();
        assertThat(auditRegistered).isGreaterThanOrEqualTo(1);
        assertThat(outboxRegistered).isNotNull();
        assertThat(outboxRegistered).isGreaterThanOrEqualTo(1);

        String auditMetadata = jdbcTemplate.queryForObject(
            "SELECT metadata::text FROM ropa.audit_events WHERE tenant_id = ? AND action = 'CROSS_BORDER_TRANSFER_REGISTERED' ORDER BY occurred_at DESC LIMIT 1",
            String.class,
            tenantId
        );
        String outboxPayload = jdbcTemplate.queryForObject(
            "SELECT payload::text FROM ropa.outbox_events WHERE tenant_id = ? AND event_type = 'ropa.cross_border_transfer_registered' ORDER BY occurred_at DESC LIMIT 1",
            String.class,
            tenantId
        );
        assertThat(auditMetadata).contains("payloadHash").contains("idempotencyKey");
        assertThat(outboxPayload).contains("payloadHash").contains("idempotencyKey");

        request.setFrequency(TransferFrequency.PERIODIC);
        request.setEndedAt(Instant.parse("2026-12-31T00:00:00Z"));

        mockMvc.perform(put("/cross-border/transfers")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "AUD-2")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        Integer auditUpdated = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.audit_events WHERE tenant_id = ? AND action = 'CROSS_BORDER_TRANSFER_UPDATED'",
            Integer.class,
            tenantId
        );
        Integer outboxUpdated = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.outbox_events WHERE tenant_id = ? AND event_type = 'ropa.cross_border_transfer_updated'",
            Integer.class,
            tenantId
        );
        assertThat(auditUpdated).isNotNull();
        assertThat(auditUpdated).isGreaterThanOrEqualTo(1);
        assertThat(outboxUpdated).isNotNull();
        assertThat(outboxUpdated).isGreaterThanOrEqualTo(1);
    }
}
