package com.regulyn.ropa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.ropa.TestSecurityConfig;
import com.regulyn.ropa.api.dto.*;
import com.regulyn.ropa.model.*;
import com.regulyn.ropa.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
@Import(TestSecurityConfig.class)
public class RopaServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ropa_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.schemas", () -> "ropa");
    }

    @Autowired
    private RopaService ropaService;

    @Autowired
    private RopaSystemRepository systemRepository;

    @Autowired
    private RopaDataCategoryRepository dataCategoryRepository;

    @Autowired
    private RopaActivityVersionRepository activityVersionRepository;

    @Autowired
    private RopaActivitySystemRepository activitySystemRepository;

    @Autowired
    private RopaActivityDataCategoryRepository activityDataCategoryRepository;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        TenantContext context = new TenantContext();
        context.setTenantId(TENANT_ID);
        context.setUserId(USER_ID);
        context.setRequestId(UUID.randomUUID().toString());
        TenantContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        activitySystemRepository.deleteAll();
        activityDataCategoryRepository.deleteAll();
        activityVersionRepository.deleteAll();
        systemRepository.deleteAll();
        dataCategoryRepository.deleteAll();
        TenantContextHolder.clear();
    }

    @Test
    void shouldCreateSystemAndList() {
        CreateSystemRequest request = new CreateSystemRequest();
        request.setSystemName("CRM Database");
        request.setSystemType(RopaSystem.SystemType.DATABASE);
        request.setLocation(RopaSystem.Location.INDIA);
        request.setCriticality(RopaSystem.Criticality.HIGH);

        SystemResponse response = ropaService.createSystem(request);

        assertThat(response.getSystemId()).isNotNull();

        List<RopaSystem> systems = ropaService.listSystems(null, null, null);
        assertThat(systems).hasSize(1);
        assertThat(systems.get(0).getSystemName()).isEqualTo("CRM Database");
    }

    @Test
    void shouldFilterSystemsByType() {
        createTestSystem("App1", RopaSystem.SystemType.APP);
        createTestSystem("DB1", RopaSystem.SystemType.DATABASE);

        List<RopaSystem> apps = ropaService.listSystems(RopaSystem.SystemType.APP, null, null);
        assertThat(apps).hasSize(1);
        assertThat(apps.get(0).getSystemType()).isEqualTo(RopaSystem.SystemType.APP);
    }

    @Test
    void shouldCreateDataCategoryAndList() {
        CreateDataCategoryRequest request = new CreateDataCategoryRequest();
        request.setCategoryKey(RopaDataCategory.CategoryKey.PII);
        request.setLabel("Personal Identifiable Information");
        request.setSensitive(true);

        DataCategoryResponse response = ropaService.createDataCategory(request);

        assertThat(response.getDataCategoryId()).isNotNull();

        List<RopaDataCategory> categories = ropaService.listDataCategories();
        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).getLabel()).isEqualTo("Personal Identifiable Information");
    }

    @Test
    void shouldCreateActivityDraftLinkAndPublish() {
        UUID systemId = createTestSystem("CRM", RopaSystem.SystemType.APP);
        UUID categoryId = createTestDataCategory(RopaDataCategory.CategoryKey.PII);

        // Create activity
        CreateActivityRequest activityRequest = new CreateActivityRequest();
        activityRequest.setActivityName("Customer Data Processing");
        activityRequest.setPurpose("Marketing and Sales");
        activityRequest.setLawfulBasis(RopaActivityVersion.LawfulBasis.CONSENT);
        activityRequest.setDataPrincipalType(RopaActivityVersion.DataPrincipalType.CUSTOMER);
        activityRequest.setRiskLevel(RopaActivityVersion.RiskLevel.MED);
        activityRequest.setRetentionDays(730);

        ActivityResponse activityResponse = ropaService.createActivity(activityRequest);
        assertThat(activityResponse.getStatus()).isEqualTo(RopaActivityVersion.Status.DRAFT);

        // Link systems and categories
        LinkActivityRequest linkRequest = new LinkActivityRequest();
        linkRequest.setSystemIds(List.of(systemId));
        linkRequest.setDataCategoryIds(List.of(categoryId));
        linkRequest.setNotes("Initial linking");

        LinkActivityResponse linkResponse = ropaService.linkActivity(activityResponse.getActivityId(), linkRequest);
        assertThat(linkResponse.getLinked()).isTrue();

        // Publish
        ActivityResponse publishResponse = ropaService.publishActivity(activityResponse.getActivityId());
        assertThat(publishResponse.getStatus()).isEqualTo(RopaActivityVersion.Status.PUBLISHED);
        assertThat(publishResponse.getPublishedAt()).isNotNull();

        // Get activity
        Map<String, Object> activity = ropaService.getActivity(activityResponse.getActivityId());
        assertThat(activity.get("status")).isEqualTo(RopaActivityVersion.Status.PUBLISHED);
        assertThat((List<?>) activity.get("systemIds")).hasSize(1);
        assertThat((List<?>) activity.get("dataCategoryIds")).hasSize(1);
    }

    @Test
    void shouldCreateNewVersionOfActivity() {
        UUID activityId = createTestActivity("Test Activity");

        // Publish first version
        ropaService.publishActivity(activityId);

        // Create new version
        CreateVersionRequest versionRequest = new CreateVersionRequest();
        versionRequest.setChangeSummary("Updated retention policy");

        CreateVersionResponse versionResponse = ropaService.createNewVersion(activityId, versionRequest);
        assertThat(versionResponse.getVersionNumber()).isEqualTo(2);

        // Verify old version is still published
        List<RopaActivityVersion> versions = activityVersionRepository.findByTenantIdAndActivityId(TENANT_ID, activityId);
        assertThat(versions).hasSize(2);

        long publishedCount = versions.stream()
            .filter(v -> v.getStatus() == RopaActivityVersion.Status.PUBLISHED)
            .count();
        assertThat(publishedCount).isEqualTo(1);

        long draftCount = versions.stream()
            .filter(v -> v.getStatus() == RopaActivityVersion.Status.DRAFT)
            .count();
        assertThat(draftCount).isEqualTo(1);
    }

    @Test
    void shouldRetireOldVersionWhenPublishingNew() {
        UUID activityId = createTestActivity("Test Activity");

        // Publish v1
        ropaService.publishActivity(activityId);

        // Create v2 and publish
        CreateVersionRequest versionRequest = new CreateVersionRequest();
        ropaService.createNewVersion(activityId, versionRequest);
        ropaService.publishActivity(activityId);

        // Verify v1 is retired and v2 is published
        List<RopaActivityVersion> versions = activityVersionRepository.findByTenantIdAndActivityId(TENANT_ID, activityId);

        RopaActivityVersion v1 = versions.stream()
            .filter(v -> v.getVersionNumber() == 1)
            .findFirst()
            .orElseThrow();
        assertThat(v1.getStatus()).isEqualTo(RopaActivityVersion.Status.RETIRED);

        RopaActivityVersion v2 = versions.stream()
            .filter(v -> v.getVersionNumber() == 2)
            .findFirst()
            .orElseThrow();
        assertThat(v2.getStatus()).isEqualTo(RopaActivityVersion.Status.PUBLISHED);
    }

    @Test
    void shouldSearchActivitiesByFilters() {
        UUID systemId1 = createTestSystem("System1", RopaSystem.SystemType.APP);
        UUID systemId2 = createTestSystem("System2", RopaSystem.SystemType.DATABASE);
        UUID categoryId = createTestDataCategory(RopaDataCategory.CategoryKey.PII);

        // Create and publish activity 1 with system1
        UUID activityId1 = createTestActivity("Activity 1");
        LinkActivityRequest link1 = new LinkActivityRequest();
        link1.setSystemIds(List.of(systemId1));
        link1.setDataCategoryIds(List.of(categoryId));
        ropaService.linkActivity(activityId1, link1);
        ropaService.publishActivity(activityId1);

        // Create and publish activity 2 with system2
        UUID activityId2 = createTestActivity("Activity 2");
        LinkActivityRequest link2 = new LinkActivityRequest();
        link2.setSystemIds(List.of(systemId2));
        ropaService.linkActivity(activityId2, link2);
        ropaService.publishActivity(activityId2);

        // Search by systemId
        Page<RopaActivityVersion> results = ropaService.listActivities(
            RopaActivityVersion.Status.PUBLISHED, null, null, null, systemId1, null, 0, 20);

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getActivityId()).isEqualTo(activityId1);
    }

    // Helper methods
    private UUID createTestSystem(String name, RopaSystem.SystemType type) {
        CreateSystemRequest request = new CreateSystemRequest();
        request.setSystemName(name);
        request.setSystemType(type);
        request.setLocation(RopaSystem.Location.INDIA);
        request.setCriticality(RopaSystem.Criticality.MED);
        return ropaService.createSystem(request).getSystemId();
    }

    private UUID createTestDataCategory(RopaDataCategory.CategoryKey key) {
        CreateDataCategoryRequest request = new CreateDataCategoryRequest();
        request.setCategoryKey(key);
        request.setLabel(key.name());
        request.setSensitive(true);
        return ropaService.createDataCategory(request).getDataCategoryId();
    }

    private UUID createTestActivity(String name) {
        CreateActivityRequest request = new CreateActivityRequest();
        request.setActivityName(name);
        request.setPurpose("Test purpose");
        request.setLawfulBasis(RopaActivityVersion.LawfulBasis.CONSENT);
        request.setDataPrincipalType(RopaActivityVersion.DataPrincipalType.CUSTOMER);
        request.setRiskLevel(RopaActivityVersion.RiskLevel.LOW);
        return ropaService.createActivity(request).getActivityId();
    }
}
