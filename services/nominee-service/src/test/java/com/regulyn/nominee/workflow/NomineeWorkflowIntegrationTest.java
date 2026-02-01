package com.regulyn.nominee.workflow;

import com.regulyn.nominee.BaseIntegrationTest;
import com.regulyn.nominee.entity.Nominee;
import com.regulyn.nominee.entity.NomineeClaim;
import com.regulyn.nominee.repository.NomineeClaimRepository;
import com.regulyn.nominee.repository.NomineeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class NomineeWorkflowIntegrationTest extends BaseIntegrationTest {

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
