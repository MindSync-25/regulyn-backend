package com.regulyn.ropa.retention;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.ropa.TestSecurityConfig;
import com.regulyn.ropa.api.dto.RetentionPolicyUpsertRequest;
import com.regulyn.ropa.model.RopaActivityVersion;
import com.regulyn.ropa.model.RopaDataCategory;
import com.regulyn.ropa.model.RopaSystem;
import com.regulyn.ropa.model.RetentionBasis;
import com.regulyn.ropa.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
public class RetentionHierarchyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ropa_retention_hierarchy_test")
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
    private RetentionPolicyActivityRepository retentionPolicyActivityRepository;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000200");

    private UUID systemId;
    private UUID activityId;
    private UUID dataCategoryId;
    private UUID purposeVersionId;

    @BeforeEach
    void setup() {
        retentionPolicyActivityRepository.deleteAll();
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
        activity.setTenantId(tenantId);
        activityId = UUID.randomUUID();
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

        purposeVersionId = UUID.randomUUID();
    }

    @Test
    void shouldResolveRetentionHierarchy() throws Exception {
        RetentionPolicyUpsertRequest systemReq = new RetentionPolicyUpsertRequest();
        systemReq.setRetentionDays(100);
        systemReq.setRetentionBasis(RetentionBasis.LEGAL_OBLIGATION);
        systemReq.setRetentionNote("system");
        systemReq.setReviewRequired(false);

        mockMvc.perform(put("/retention/systems/{systemId}", systemId)
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "SYS-1")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(systemReq)))
            .andExpect(status().isOk());

        RetentionPolicyUpsertRequest activityReq = new RetentionPolicyUpsertRequest();
        activityReq.setRetentionDays(200);
        activityReq.setRetentionBasis(RetentionBasis.CONTRACT);
        activityReq.setRetentionNote("activity");
        activityReq.setReviewRequired(false);

        mockMvc.perform(put("/retention/activities/{activityId}", activityId)
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "ACT-1")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(activityReq)))
            .andExpect(status().isOk());

        RetentionPolicyUpsertRequest categoryReq = new RetentionPolicyUpsertRequest();
        categoryReq.setRetentionDays(300);
        categoryReq.setRetentionBasis(RetentionBasis.CONSENT);
        categoryReq.setRetentionNote("category");
        categoryReq.setReviewRequired(true);

        mockMvc.perform(put("/retention/categories/{dataCategoryId}/purposes/{purposeVersionId}", dataCategoryId, purposeVersionId)
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "CAT-1")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(categoryReq)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/retention/resolve")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .param("systemId", systemId.toString())
                .param("activityId", activityId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.effective.level").value("ACTIVITY"))
            .andExpect(jsonPath("$.effective.retentionDays").value(200));

        mockMvc.perform(get("/retention/resolve")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .param("systemId", systemId.toString())
                .param("activityId", activityId.toString())
                .param("dataCategoryId", dataCategoryId.toString())
                .param("purposeVersionId", purposeVersionId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.effective.level").value("CATEGORY_PURPOSE"))
            .andExpect(jsonPath("$.effective.retentionDays").value(300));

        retentionPolicyActivityRepository.deleteAll();

        mockMvc.perform(get("/retention/resolve")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .param("systemId", systemId.toString())
                .param("activityId", activityId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.effective.level").value("SYSTEM"))
            .andExpect(jsonPath("$.effective.retentionDays").value(100));

        String first = mockMvc.perform(get("/retention/resolve")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .param("systemId", systemId.toString())
                .param("activityId", activityId.toString()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(get("/retention/resolve")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .param("systemId", systemId.toString())
                .param("activityId", activityId.toString()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(first).isEqualTo(second);
    }
}
