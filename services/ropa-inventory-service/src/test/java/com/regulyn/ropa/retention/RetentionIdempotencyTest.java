package com.regulyn.ropa.retention;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.ropa.TestSecurityConfig;
import com.regulyn.ropa.api.dto.RetentionPolicyUpsertRequest;
import com.regulyn.ropa.model.RopaSystem;
import com.regulyn.ropa.model.RetentionBasis;
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

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
public class RetentionIdempotencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ropa_retention_idem_test")
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

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000300");
    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000400");

    private UUID systemId;

    @BeforeEach
    void setup() {
        systemRepository.deleteAll();

        RopaSystem system = new RopaSystem();
        system.setTenantId(tenantId);
        system.setSystemName("CRM");
        system.setSystemType(RopaSystem.SystemType.DATABASE);
        system.setLocation(RopaSystem.Location.INDIA);
        system.setCriticality(RopaSystem.Criticality.HIGH);
        systemRepository.save(system);
        systemId = system.getSystemId();
    }

    @Test
    void shouldEnforceIdempotency() throws Exception {
        RetentionPolicyUpsertRequest request = new RetentionPolicyUpsertRequest();
        request.setRetentionDays(365);
        request.setRetentionBasis(RetentionBasis.LEGAL_OBLIGATION);
        request.setRetentionNote("tax");
        request.setReviewRequired(false);

        mockMvc.perform(put("/retention/systems/{systemId}", systemId)
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "K1")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.created").value(true));

        mockMvc.perform(put("/retention/systems/{systemId}", systemId)
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "K1")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.created").value(false));

        RetentionPolicyUpsertRequest different = new RetentionPolicyUpsertRequest();
        different.setRetentionDays(400);
        different.setRetentionBasis(RetentionBasis.LEGAL_OBLIGATION);
        different.setRetentionNote("tax");
        different.setReviewRequired(false);

        mockMvc.perform(put("/retention/systems/{systemId}", systemId)
                .with(user("tester"))
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", userId.toString())
                .header("X-Idempotency-Key", "K1")
                .header("X-Tenant-Id", tenantId.toString())
                .header("X-Actor-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(different)))
            .andExpect(status().isConflict());
    }
}
