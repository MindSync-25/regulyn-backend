package com.regulyn.guardian.workflow;

import com.regulyn.guardian.config.TestConfig;
import com.regulyn.guardian.entity.Child;
import com.regulyn.guardian.entity.GuardianConsent;
import com.regulyn.guardian.entity.Guardian;
import com.regulyn.guardian.entity.ConsentStatus;
import com.regulyn.guardian.entity.VerificationStatus;
import com.regulyn.guardian.repository.ChildRepository;
import com.regulyn.guardian.repository.GuardianConsentRepository;
import com.regulyn.guardian.repository.GuardianRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(TestConfig.class)
public class GuardianWorkflowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("guardian")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ChildRepository childRepository;

    @Autowired
    private GuardianRepository guardianRepository;

    @Autowired
    private GuardianConsentRepository guardianConsentRepository;

    @Test
    public void testGuardianWorkflow() {
        UUID tenantId = UUID.randomUUID();
        
        // Create child
        Child child = new Child();
        child.setId(UUID.randomUUID());
        child.setTenantId(tenantId);
        child.setChildReferenceId("TEST-CHILD-001");
        child.setDateOfBirth(LocalDate.now().minusYears(10)); // 10 years old
        child.setAgeYears(10);
        child.setMinor(true);
        child.setRequiresGuardianConsent(true);
        child.setCreatedAt(Instant.now());
        child.setUpdatedAt(Instant.now());

        Child savedChild = childRepository.save(child);

        assertThat(savedChild.getId()).isNotNull();
        assertThat(savedChild.isMinor()).isTrue();
        assertThat(savedChild.isRequiresGuardianConsent()).isTrue();

        // Create guardian
        Guardian guardian = new Guardian();
        guardian.setId(UUID.randomUUID());
        guardian.setTenantId(tenantId);
        guardian.setChildId(savedChild.getId());
        guardian.setEmail("guardian@example.com");
        guardian.setPhoneNumber("+1234567890");
        guardian.setRelationshipType("PARENT");
        guardian.setVerificationStatus(VerificationStatus.VERIFIED);
        guardian.setCreatedAt(Instant.now());
        guardian.setUpdatedAt(Instant.now());
        guardian.setVerifiedAt(Instant.now());

        Guardian savedGuardian = guardianRepository.save(guardian);

        assertThat(savedGuardian.getId()).isNotNull();
        assertThat(savedGuardian.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);

        // Create consent
        GuardianConsent consent = new GuardianConsent();
        consent.setId(UUID.randomUUID());
        consent.setTenantId(tenantId);
        consent.setChildId(savedChild.getId());
        consent.setGuardianId(savedGuardian.getId());
        consent.setPurposeKey("DATA_PROCESSING");
        consent.setStatus(ConsentStatus.APPROVED);
        consent.setCreatedAt(Instant.now());
        consent.setUpdatedAt(Instant.now());
        consent.setApprovedAt(Instant.now());
        consent.setIdempotencyKey(UUID.randomUUID().toString());

        GuardianConsent savedConsent = guardianConsentRepository.save(consent);

        assertThat(savedConsent.getId()).isNotNull();
        assertThat(savedConsent.getStatus()).isEqualTo(ConsentStatus.APPROVED);
    }
}

