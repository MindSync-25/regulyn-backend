package com.regulyn.retention.cascade;

import com.regulyn.retention.config.TestSecurityNoMockConfig;
import com.regulyn.retention.entity.DeletionRequest;
import com.regulyn.retention.persistence.AbstractPostgresIT;
import com.regulyn.retention.repository.DeletionExecutionPlanRepository;
import com.regulyn.retention.repository.DeletionRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(value = "test-security-nomock", inheritProfiles = false)
@Import(TestSecurityNoMockConfig.class)
public class NoSystemsConfiguredCascadeTest extends AbstractPostgresIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DeletionRequestRepository deletionRequestRepository;

    @Autowired
    private DeletionExecutionPlanRepository planRepository;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @DynamicPropertySource
    static void configureCascadeProperties(DynamicPropertyRegistry registry) {
        registry.add("connector.service.url", () -> "http://localhost:59999");
        registry.add("retention.scheduler.enabled", () -> "false");
        registry.add("audit.schema", () -> "deletion");
    }

    @Test
    void noSystemsConfiguredReturnsBadRequest() {
        DeletionRequest deletion = new DeletionRequest();
        deletion.setTenantId(tenantId);
        deletion.setSubjectId(UUID.randomUUID());
        deletion.setSubjectType("CUSTOMER");
        deletion.setEntityType("USER_PROFILE");
        deletion.setSource("DSAR");
        deletion.setStatus("APPROVED");
        deletion.setRequiresApproval(true);
        deletion.setApprovedBy(actorId);
        deletion.setApprovedAt(java.time.Instant.now());
        deletion = deletionRequestRepository.save(deletion);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        headers.set("X-User-ID", actorId.toString());
        headers.set("X-Idempotency-Key", "cascade-no-systems");
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/deletions/" + deletion.getDeletionId() + "/cascade-execute",
                HttpMethod.POST,
            new HttpEntity<>(null, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(planRepository.findByTenantIdAndIdempotencyKey(tenantId, "cascade-no-systems")).isEmpty();
    }
}
