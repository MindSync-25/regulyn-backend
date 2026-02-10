package com.regulyn.ropa.crossborder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.ropa.TestSecurityConfig;
import com.regulyn.ropa.api.dto.CrossBorderTransferUpsertRequest;
import com.regulyn.ropa.model.RopaActivityVersion;
import com.regulyn.ropa.model.RopaDataCategory;
import com.regulyn.ropa.model.RopaSystem;
import com.regulyn.ropa.model.TransferFrequency;
import com.regulyn.ropa.model.TransferMechanism;
import com.regulyn.ropa.repository.CrossBorderTransferDataCategoryRepository;
import com.regulyn.ropa.repository.CrossBorderTransferPurposeVersionRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
public class CrossBorderUpsertAndQueryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ropa_cross_border_query_test")
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
    private CrossBorderTransferDataCategoryRepository transferDataCategoryRepository;

    @Autowired
    private CrossBorderTransferPurposeVersionRepository transferPurposeVersionRepository;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000001100");
    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000001200");

    private UUID systemId;
    private UUID activityId;
    private UUID dataCategory1;
    private UUID dataCategory2;

    @BeforeEach
    void setup() {
        transferPurposeVersionRepository.deleteAll();
        transferDataCategoryRepository.deleteAll();
        transferRepository.deleteAll();
        activityVersionRepository.deleteAll();
        systemRepository.deleteAll();
        dataCategoryRepository.deleteAll();

        RopaSystem system = new RopaSystem();
        system.setTenantId(tenantId);
        system.setSystemName("Payments");
        system.setSystemType(RopaSystem.SystemType.APP);
        system.setLocation(RopaSystem.Location.INDIA);
        system.setCriticality(RopaSystem.Criticality.MED);
        systemRepository.save(system);
        systemId = system.getSystemId();

        RopaActivityVersion activity = new RopaActivityVersion();
        activityId = UUID.randomUUID();
        activity.setTenantId(tenantId);
        activity.setActivityId(activityId);
        activity.setVersionNumber(1);
        activity.setStatus(RopaActivityVersion.Status.PUBLISHED);
        activity.setActivityName("Payments processing");
        activity.setPurpose("Card processing");
        activity.setLawfulBasis(RopaActivityVersion.LawfulBasis.CONTRACT);
        activity.setDataPrincipalType(RopaActivityVersion.DataPrincipalType.CUSTOMER);
        activity.setRiskLevel(RopaActivityVersion.RiskLevel.MED);
        activityVersionRepository.save(activity);

        RopaDataCategory category1 = new RopaDataCategory();
        category1.setTenantId(tenantId);
        category1.setCategoryKey(RopaDataCategory.CategoryKey.PII);
        category1.setLabel("PII");
        category1.setSensitive(true);
        dataCategoryRepository.save(category1);
        dataCategory1 = category1.getDataCategoryId();

        RopaDataCategory category2 = new RopaDataCategory();
        category2.setTenantId(tenantId);
        category2.setCategoryKey(RopaDataCategory.CategoryKey.FINANCIAL);
        category2.setLabel("PII-SECONDARY");
        category2.setSensitive(true);
        dataCategoryRepository.save(category2);
        dataCategory2 = category2.getDataCategoryId();
    }

    @Test
    void shouldUpsertAndQueryTransfers() throws Exception {
        UUID vendorId = UUID.randomUUID();
        List<UUID> purposeVersionIds = List.of(UUID.randomUUID(), UUID.randomUUID());

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
        request.setEndedAt(null);
        request.setDataCategoryIds(List.of(dataCategory1, dataCategory2));
        request.setPurposeVersionIds(purposeVersionIds);

        String responseBody = mockMvc.perform(put("/cross-border/transfers")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "CBT-1")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.created").value(true))
            .andExpect(jsonPath("$.transferId").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();

        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        UUID transferId = UUID.fromString(response.get("transferId").toString());

        assertThat(transferDataCategoryRepository.findByTenantIdAndIdTransferId(tenantId, transferId)).hasSize(2);
        assertThat(transferPurposeVersionRepository.findByTenantIdAndIdTransferId(tenantId, transferId)).hasSize(2);

        mockMvc.perform(get("/cross-border/transfers")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .param("vendorId", vendorId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()" ).value(1));

        mockMvc.perform(get("/cross-border/transfers")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .param("dataCategoryId", dataCategory1.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()" ).value(1));

        mockMvc.perform(get("/cross-border/transfers")
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .param("purposeVersionId", purposeVersionIds.getFirst().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()" ).value(1));

        mockMvc.perform(get("/cross-border/transfers/{transferId}", transferId)
                .with(user("tester"))
                .header("X-Tenant-ID", UUID.fromString("00000000-0000-0000-0000-000000009999").toString())
                .header("X-User-ID", userId.toString())
                .header("X-Tenant-Id", UUID.fromString("00000000-0000-0000-0000-000000009999").toString())
                .header("X-Actor-Id", userId.toString()))
            .andExpect(status().isNotFound());
    }
}
