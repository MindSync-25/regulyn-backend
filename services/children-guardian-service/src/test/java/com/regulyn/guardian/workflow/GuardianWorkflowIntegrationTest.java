package com.regulyn.guardian.workflow;

import com.regulyn.guardian.config.TestConfig;
import com.regulyn.guardian.entity.ChildProfile;
import com.regulyn.guardian.entity.GuardianConsent;
import com.regulyn.guardian.entity.GuardianVerification;
import com.regulyn.guardian.repository.ChildProfileRepository;
import com.regulyn.guardian.repository.GuardianConsentRepository;
import com.regulyn.guardian.repository.GuardianVerificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
    private ChildProfileRepository childProfileRepository;

    @Autowired
    private GuardianVerificationRepository guardianVerificationRepository;

    @Autowired
    private GuardianConsentRepository guardianConsentRepository;

    @Test
    public void testGuardianWorkflow() {
        ChildProfile child = new ChildProfile();
        child.setTenantId(UUID.randomUUID());
        child.setUserId(UUID.randomUUID());
        child.setDob(LocalDate.now().minusYears(10)); // 10 years old

        ChildProfile savedChild = childProfileRepository.save(child);

        assertThat(savedChild.getId()).isNotNull();
        assertThat(savedChild.getIsChild()).isTrue(); // Generated column
        assertThat(savedChild.getRegisteredAt()).isNotNull();

        GuardianVerification verification = new GuardianVerification();
        verification.setTenantId(savedChild.getTenantId());
        verification.setChildProfileId(savedChild.getId());
        verification.setGuardianId(UUID.randomUUID());
        verification.setStatus("PENDING");

        GuardianVerification savedVerification = guardianVerificationRepository.save(verification);

        assertThat(savedVerification.getId()).isNotNull();
        assertThat(savedVerification.getSubmittedAt()).isNotNull();

        GuardianConsent consent = new GuardianConsent();
        consent.setTenantId(savedChild.getTenantId());
        consent.setChildProfileId(savedChild.getId());
        consent.setGuardianId(savedVerification.getGuardianId());
        consent.setStatus("GRANTED");

        GuardianConsent savedConsent = guardianConsentRepository.save(consent);

        assertThat(savedConsent.getId()).isNotNull();
        assertThat(savedConsent.getSignedAt()).isNotNull();
    }
}
