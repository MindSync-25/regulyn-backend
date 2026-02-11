package com.regulyn.guardian.age;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.guardian.config.TestPermitAllSecurityConfig;
import com.regulyn.guardian.config.TestSecurityConfig;
import com.regulyn.guardian.dto.AgeEvaluationRequest;
import com.regulyn.guardian.dto.AgeEvaluationResponse;
import com.regulyn.guardian.dto.AgeRuleUpsertRequest;
import com.regulyn.guardian.dto.CreateConsentRequest;
import com.regulyn.guardian.dto.CreateConsentResponse;
import com.regulyn.guardian.entity.Child;
import com.regulyn.guardian.entity.Guardian;
import com.regulyn.guardian.repository.ChildRepository;
import com.regulyn.guardian.repository.GuardianConsentRepository;
import com.regulyn.guardian.repository.GuardianRepository;
import com.regulyn.guardian.service.ConsentStateMachine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import({TestSecurityConfig.class, TestPermitAllSecurityConfig.class})
class ChildrenGuardianRound2Part3AgeRulesIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.schemas", () -> "public,guardian,children");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.task.scheduling.enabled", () -> false);
        registry.add("audit.schema", () -> "guardian");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChildRepository childRepository;

    @Autowired
    private GuardianRepository guardianRepository;

    @Autowired
    private GuardianConsentRepository consentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void defaultThresholdAllowsMinorConsent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Child child = seedChild(tenantId, LocalDate.now().minusYears(17), "US", "CA");
        Guardian guardian = seedGuardian(tenantId, child.getChildId());

        CreateConsentRequest request = new CreateConsentRequest(
                child.getChildId(),
                guardian.getGuardianId(),
                "DATA_PROCESSING",
                "FULL",
                null,
                null,
                null,
                true,
                null,
                null,
                null
        );

        ResponseEntity<CreateConsentResponse> response = restTemplate.exchange(
                "/consents",
                HttpMethod.POST,
                new HttpEntity<>(request, tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"))),
                CreateConsentResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM guardian.audit_events WHERE tenant_id = ? AND action = ?",
                Integer.class,
                tenantId,
                "CHILD_AGE_EVALUATED"
        );
        assertThat(auditCount).isNotNull();
        assertThat(auditCount).isGreaterThan(0);

        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId,
                "child.age_evaluated"
        );
        assertThat(outboxCount).isNotNull();
        assertThat(outboxCount).isGreaterThan(0);
    }

    @Test
    void stateOverrideBlocksAdultGuardianConsent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AgeRuleUpsertRequest rule = new AgeRuleUpsertRequest("US", "CA", (short) 16, false);
        ResponseEntity<String> ruleResponse = restTemplate.exchange(
                "/age-rules",
                HttpMethod.PUT,
                new HttpEntity<>(rule, tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"))),
                String.class
        );
        assertThat(ruleResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Child child = seedChild(tenantId, LocalDate.now().minusYears(17), "US", "CA");
        Guardian guardian = seedGuardian(tenantId, child.getChildId());

        CreateConsentRequest request = new CreateConsentRequest(
                child.getChildId(),
                guardian.getGuardianId(),
                "DATA_PROCESSING",
                "FULL",
                null,
                null,
                null,
                true,
                null,
                null,
                null
        );

        ResponseEntity<String> response = restTemplate.exchange(
                "/consents",
                HttpMethod.POST,
                new HttpEntity<>(request, tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        Integer consentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM children.guardian_consents WHERE tenant_id = ?",
                Integer.class,
                tenantId
        );
        assertThat(consentCount).isNotNull();
        assertThat(consentCount).isZero();

        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId,
                "child.age_evaluated"
        );
        assertThat(outboxCount).isNotNull();
        assertThat(outboxCount).isGreaterThan(0);
    }

    @Test
    void countryRuleFallbackResolvesCorrectly() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AgeRuleUpsertRequest rule = new AgeRuleUpsertRequest("US", null, (short) 13, false);
        ResponseEntity<String> ruleResponse = restTemplate.exchange(
                "/age-rules",
                HttpMethod.PUT,
                new HttpEntity<>(rule, tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"))),
                String.class
        );
        assertThat(ruleResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Child child = seedChild(tenantId, LocalDate.now().minusYears(14), "US", "NY");
        Guardian guardian = seedGuardian(tenantId, child.getChildId());

        AgeEvaluationRequest evaluationRequest = new AgeEvaluationRequest(null, null, LocalDate.now());
        ResponseEntity<AgeEvaluationResponse> evaluationResponse = restTemplate.exchange(
                "/children/" + child.getChildId() + "/age-evaluation",
                HttpMethod.POST,
                new HttpEntity<>(evaluationRequest, tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"))),
                AgeEvaluationResponse.class
        );

        assertThat(evaluationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(evaluationResponse.getBody()).isNotNull();
        assertThat(evaluationResponse.getBody().resolvedFrom()).isEqualTo("COUNTRY_RULE");
        assertThat(evaluationResponse.getBody().isMinor()).isFalse();

        CreateConsentRequest request = new CreateConsentRequest(
                child.getChildId(),
                guardian.getGuardianId(),
                "DATA_PROCESSING",
                "FULL",
                null,
                null,
                null,
                true,
                null,
                null,
                null
        );

        ResponseEntity<String> response = restTemplate.exchange(
                "/consents",
                HttpMethod.POST,
                new HttpEntity<>(request, tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void defaultRuleIsUniquePerTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AgeRuleUpsertRequest firstDefault = new AgeRuleUpsertRequest("US", null, (short) 18, true);
        AgeRuleUpsertRequest secondDefault = new AgeRuleUpsertRequest("US", "CA", (short) 16, true);

        ResponseEntity<String> firstResponse = restTemplate.exchange(
                "/age-rules",
                HttpMethod.PUT,
                new HttpEntity<>(firstDefault, tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"))),
                String.class
        );
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> secondResponse = restTemplate.exchange(
                "/age-rules",
                HttpMethod.PUT,
                new HttpEntity<>(secondDefault, tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"))),
                String.class
        );
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer defaultCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM children.age_threshold_rules WHERE tenant_id = ? AND is_default = true",
                Integer.class,
                tenantId
        );
        assertThat(defaultCount).isNotNull();
        assertThat(defaultCount).isEqualTo(1);

        Integer auditCreated = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM guardian.audit_events WHERE tenant_id = ? AND action = ?",
                Integer.class,
                tenantId,
                "AGE_RULE_CREATED"
        );
        assertThat(auditCreated).isNotNull();
        assertThat(auditCreated).isGreaterThan(0);

        Integer outboxUpdated = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId,
                "age_rule.updated"
        );
        assertThat(outboxUpdated).isNotNull();
        assertThat(outboxUpdated).isGreaterThan(0);
    }

    @Test
    void legalBoundsEnforced() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AgeRuleUpsertRequest tooLow = new AgeRuleUpsertRequest("US", null, (short) 12, false);
        ResponseEntity<String> lowResponse = restTemplate.exchange(
                "/age-rules",
                HttpMethod.PUT,
                new HttpEntity<>(tooLow, tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"))),
                String.class
        );
        assertThat(lowResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        AgeRuleUpsertRequest tooHigh = new AgeRuleUpsertRequest("US", null, (short) 19, false);
        ResponseEntity<String> highResponse = restTemplate.exchange(
                "/age-rules",
                HttpMethod.PUT,
                new HttpEntity<>(tooHigh, tenantContextHeaders(tenantId, userId, Set.of("TENANT_ADMIN"))),
                String.class
        );
        assertThat(highResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Child seedChild(UUID tenantId, LocalDate dob, String country, String state) {
        Child child = new Child();
        child.setTenantId(tenantId);
        child.setChildRef("CHILD-" + UUID.randomUUID());
        child.setFullName("Test Child");
        child.setDateOfBirth(dob);
        child.setCountry(country);
        child.setRegionCountryCode(country);
        child.setRegionStateCode(state);
        child.setStatus("ACTIVE");
        return childRepository.save(child);
    }

    private Guardian seedGuardian(UUID tenantId, UUID childId) {
        Guardian guardian = new Guardian();
        guardian.setTenantId(tenantId);
        guardian.setChildId(childId);
        guardian.setGuardianName("Test Guardian");
        guardian.setGuardianEmail("guardian@example.com");
        guardian.setGuardianPhone("+10000000000");
        guardian.setRelationship("PARENT");
        guardian.setStatus(ConsentStateMachine.GUARDIAN_VERIFIED);
        guardian.setVerifiedAt(java.time.Instant.now());
        guardian.setVerifiedBy(UUID.randomUUID());
        return guardianRepository.save(guardian);
    }

    private HttpHeaders tenantContextHeaders(UUID tenantId, UUID userId, Set<String> roles) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        Map<String, Object> contextMap = Map.of(
                "tenantId", tenantId.toString(),
                "userId", userId.toString(),
                "roles", roles
        );
        headers.set("X-Tenant-Context", objectMapper.writeValueAsString(contextMap));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
