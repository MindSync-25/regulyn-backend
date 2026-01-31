package com.regulyn.nominee.workflow;

import com.regulyn.nominee.entity.Nominee;
import com.regulyn.nominee.entity.NomineeClaim;
import com.regulyn.nominee.repository.NomineeClaimRepository;
import com.regulyn.nominee.repository.NomineeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class NomineeWorkflowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("nominee")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private NomineeRepository nomineeRepository;

    @Autowired
    private NomineeClaimRepository nomineeClaimRepository;

    @Test
    public void testNomineeWorkflow() {
        Nominee nominee = new Nominee();
        nominee.setTenantId(UUID.randomUUID());
        nominee.setDataPrincipalId(UUID.randomUUID());
        nominee.setNomineeName("John Doe");
        nominee.setStatus("REGISTERED");

        Nominee saved = nomineeRepository.save(nominee);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRegisteredAt()).isNotNull();

        NomineeClaim claim = new NomineeClaim();
        claim.setTenantId(saved.getTenantId());
        claim.setNomineeId(saved.getId());
        claim.setClaimType("DATA_ACCESS");
        claim.setStatus("CLAIM_SUBMITTED");

        NomineeClaim savedClaim = nomineeClaimRepository.save(claim);

        assertThat(savedClaim.getId()).isNotNull();
        assertThat(savedClaim.getSubmittedAt()).isNotNull();
    }
}
