package com.regulyn.ropa.retention;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.ropa.TestSecurityConfig;
import com.regulyn.ropa.api.dto.RetentionMatrixExportRequest;
import com.regulyn.ropa.model.RopaActivityDataCategory;
import com.regulyn.ropa.model.RopaActivitySystem;
import com.regulyn.ropa.model.RopaActivityVersion;
import com.regulyn.ropa.model.RopaDataCategory;
import com.regulyn.ropa.model.RopaSystem;
import com.regulyn.ropa.repository.*;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
public class RetentionMatrixExportIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ropa_retention_matrix_test")
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
    private RopaActivitySystemRepository activitySystemRepository;

    @Autowired
    private RopaActivityDataCategoryRepository activityDataCategoryRepository;

    @Autowired
    private RopaDataCategoryRepository dataCategoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000700");
    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000800");

    private UUID system1Id;
    private UUID system2Id;
    private UUID activity1Id;
    private UUID activity2Id;
    private UUID dataCategoryId;

    @BeforeEach
    void setup() {
        activitySystemRepository.deleteAll();
        activityDataCategoryRepository.deleteAll();
        activityVersionRepository.deleteAll();
        systemRepository.deleteAll();
        dataCategoryRepository.deleteAll();

        RopaSystem system1 = new RopaSystem();
        system1.setTenantId(tenantId);
        system1.setSystemName("CRM");
        system1.setSystemType(RopaSystem.SystemType.DATABASE);
        system1.setLocation(RopaSystem.Location.INDIA);
        system1.setCriticality(RopaSystem.Criticality.HIGH);
        systemRepository.save(system1);
        system1Id = system1.getSystemId();

        RopaSystem system2 = new RopaSystem();
        system2.setTenantId(tenantId);
        system2.setSystemName("ERP");
        system2.setSystemType(RopaSystem.SystemType.APP);
        system2.setLocation(RopaSystem.Location.INDIA);
        system2.setCriticality(RopaSystem.Criticality.MED);
        systemRepository.save(system2);
        system2Id = system2.getSystemId();

        RopaDataCategory category = new RopaDataCategory();
        category.setTenantId(tenantId);
        category.setCategoryKey(RopaDataCategory.CategoryKey.PII);
        category.setLabel("PII");
        category.setSensitive(true);
        dataCategoryRepository.save(category);
        dataCategoryId = category.getDataCategoryId();

        RopaActivityVersion activity1 = new RopaActivityVersion();
        activity1.setTenantId(tenantId);
        activity1Id = UUID.randomUUID();
        activity1.setActivityId(activity1Id);
        activity1.setVersionNumber(1);
        activity1.setStatus(RopaActivityVersion.Status.PUBLISHED);
        activity1.setActivityName("Onboarding");
        activity1.setPurpose("KYC");
        activity1.setLawfulBasis(RopaActivityVersion.LawfulBasis.CONTRACT);
        activity1.setDataPrincipalType(RopaActivityVersion.DataPrincipalType.CUSTOMER);
        activity1.setRiskLevel(RopaActivityVersion.RiskLevel.MED);
        activityVersionRepository.save(activity1);

        RopaActivityVersion activity2 = new RopaActivityVersion();
        activity2.setTenantId(tenantId);
        activity2Id = UUID.randomUUID();
        activity2.setActivityId(activity2Id);
        activity2.setVersionNumber(1);
        activity2.setStatus(RopaActivityVersion.Status.PUBLISHED);
        activity2.setActivityName("Support");
        activity2.setPurpose("Support");
        activity2.setLawfulBasis(RopaActivityVersion.LawfulBasis.LEGITIMATE_INTERESTS);
        activity2.setDataPrincipalType(RopaActivityVersion.DataPrincipalType.CUSTOMER);
        activity2.setRiskLevel(RopaActivityVersion.RiskLevel.LOW);
        activityVersionRepository.save(activity2);

        RopaActivitySystem link1 = new RopaActivitySystem();
        link1.setTenantId(tenantId);
        link1.setVersionId(activity1.getVersionId());
        link1.setSystemId(system1Id);
        activitySystemRepository.save(link1);

        RopaActivitySystem link2 = new RopaActivitySystem();
        link2.setTenantId(tenantId);
        link2.setVersionId(activity2.getVersionId());
        link2.setSystemId(system2Id);
        activitySystemRepository.save(link2);

        RopaActivityDataCategory cat1 = new RopaActivityDataCategory();
        cat1.setTenantId(tenantId);
        cat1.setVersionId(activity1.getVersionId());
        cat1.setDataCategoryId(dataCategoryId);
        activityDataCategoryRepository.save(cat1);

        RopaActivityDataCategory cat2 = new RopaActivityDataCategory();
        cat2.setTenantId(tenantId);
        cat2.setVersionId(activity2.getVersionId());
        cat2.setDataCategoryId(dataCategoryId);
        activityDataCategoryRepository.save(cat2);
    }

    @Test
    void shouldExportMatrixAndWriteEvents() throws Exception {
        RetentionMatrixExportRequest request = new RetentionMatrixExportRequest();
        request.setSystemIds(List.of(system1Id, system2Id));
        request.setActivityStatus(RopaActivityVersion.Status.PUBLISHED);
        request.setIncludeDisabled(false);

        String response = mockMvc.perform(post("/retention/exports/matrix")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "MATRIX-1")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andExpect(jsonPath("$.rows.length()").value(2))
            .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("\"status\":\"CREATED\"");

        Integer exportCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.ropa_report_exports WHERE tenant_id = ? AND report_type = 'RETENTION_MATRIX' AND status = 'CREATED'",
            Integer.class,
            tenantId
        );
        assertThat(exportCount).isNotNull();
        assertThat(exportCount).isGreaterThanOrEqualTo(1);

        Integer requestedAudit = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.audit_events WHERE tenant_id = ? AND action = 'RETENTION_MATRIX_EXPORT_REQUESTED'",
            Integer.class,
            tenantId
        );
        Integer createdAudit = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.audit_events WHERE tenant_id = ? AND action = 'RETENTION_MATRIX_EXPORT_CREATED'",
            Integer.class,
            tenantId
        );
        assertThat(requestedAudit).isNotNull();
        assertThat(createdAudit).isNotNull();
        assertThat(requestedAudit).isGreaterThanOrEqualTo(1);
        assertThat(createdAudit).isGreaterThanOrEqualTo(1);

        Integer requestedOutbox = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.outbox_events WHERE tenant_id = ? AND event_type = 'ropa.retention_matrix_export_requested'",
            Integer.class,
            tenantId
        );
        Integer createdOutbox = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ropa.outbox_events WHERE tenant_id = ? AND event_type = 'ropa.retention_matrix_export_created'",
            Integer.class,
            tenantId
        );
        assertThat(requestedOutbox).isNotNull();
        assertThat(createdOutbox).isNotNull();
        assertThat(requestedOutbox).isGreaterThanOrEqualTo(1);
        assertThat(createdOutbox).isGreaterThanOrEqualTo(1);
    }
}
