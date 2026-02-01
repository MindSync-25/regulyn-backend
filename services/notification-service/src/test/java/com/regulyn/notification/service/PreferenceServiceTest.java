package com.regulyn.notification.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.notification.config.TestSecurityConfig;
import com.regulyn.notification.dto.GetPreferencesResponse;
import com.regulyn.notification.dto.OptOutRequest;
import com.regulyn.notification.dto.OptOutResponse;
import com.regulyn.notification.repository.CommunicationPreferenceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(TestSecurityConfig.class)
class PreferenceServiceTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("notification_test")
        .withUsername("test")
        .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.schemas", () -> "notification");
    }
    
    @Autowired
    private PreferenceService preferenceService;
    
    @Autowired
    private CommunicationPreferenceRepository preferenceRepository;
    
    @BeforeEach
    void setUp() {
        TenantContext context = new TenantContext();
        context.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        context.setUserId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        context.setRequestId("request-001");
        TenantContextHolder.setContext(context);
    }
    
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        preferenceRepository.deleteAll();
    }
    
    @Test
    void shouldOptOutFromMarketing() {
        // Given
        OptOutRequest request = new OptOutRequest(
            "dp-001",
            "EMAIL",
            "MARKETING",
            true
        );
        
        // When
        OptOutResponse response = preferenceService.updatePreference(request);
        
        // Then
        assertThat(response.preferenceId()).isNotNull();
        assertThat(preferenceService.isOptedOut("dp-001", "EMAIL", "MARKETING")).isTrue();
    }
    
    @Test
    void shouldOptInFromMarketing() {
        // Given - first opt out
        preferenceService.updatePreference(new OptOutRequest("dp-001", "EMAIL", "MARKETING", true));
        
        // When - opt back in
        OptOutRequest request = new OptOutRequest("dp-001", "EMAIL", "MARKETING", false);
        preferenceService.updatePreference(request);
        
        // Then
        assertThat(preferenceService.isOptedOut("dp-001", "EMAIL", "MARKETING")).isFalse();
    }
    
    @Test
    void shouldHandleMultipleChannelsAndCategories() {
        // Given
        preferenceService.updatePreference(new OptOutRequest("dp-001", "EMAIL", "MARKETING", true));
        preferenceService.updatePreference(new OptOutRequest("dp-001", "SMS", "MARKETING", true));
        preferenceService.updatePreference(new OptOutRequest("dp-001", "EMAIL", "LEGAL", false));
        
        // When
        GetPreferencesResponse response = preferenceService.getPreferences("dp-001");
        
        // Then
        assertThat(response.dataPrincipalId()).isEqualTo("dp-001");
        assertThat(response.preferences()).hasSize(3);
        assertThat(response.preferences())
            .anyMatch(p -> p.channel().equals("EMAIL") && p.category().equals("MARKETING") && p.optedOut())
            .anyMatch(p -> p.channel().equals("SMS") && p.category().equals("MARKETING") && p.optedOut())
            .anyMatch(p -> p.channel().equals("EMAIL") && p.category().equals("LEGAL") && !p.optedOut());
    }
    
    @Test
    void shouldUpsertPreferenceOnMultipleUpdates() {
        // Given
        UUID preferenceId1 = preferenceService.updatePreference(
            new OptOutRequest("dp-001", "EMAIL", "MARKETING", true)
        ).preferenceId();
        
        // When - update same preference
        UUID preferenceId2 = preferenceService.updatePreference(
            new OptOutRequest("dp-001", "EMAIL", "MARKETING", false)
        ).preferenceId();
        
        // Then - should be the same record
        assertThat(preferenceId1).isEqualTo(preferenceId2);
        assertThat(preferenceRepository.count()).isEqualTo(1);
    }
    
    @Test
    void shouldReturnEmptyPreferencesForNewDataPrincipal() {
        // When
        GetPreferencesResponse response = preferenceService.getPreferences("dp-new");
        
        // Then
        assertThat(response.dataPrincipalId()).isEqualTo("dp-new");
        assertThat(response.preferences()).isEmpty();
    }
}
