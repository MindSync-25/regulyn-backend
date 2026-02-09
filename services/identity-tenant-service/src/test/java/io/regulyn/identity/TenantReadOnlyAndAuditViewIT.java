package io.regulyn.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.regulyn.identity.constants.TenantStatuses;
import io.regulyn.identity.dto.ApiKeyCreateRequest;
import io.regulyn.identity.dto.ApiKeyCreateResponse;
import io.regulyn.identity.dto.AuditEventPageResponse;
import io.regulyn.identity.dto.CreateInviteRequest;
import io.regulyn.identity.dto.CreateUserRequest;
import io.regulyn.identity.dto.TenantFeatureFlagRequest;
import io.regulyn.identity.dto.TenantFeatureFlagResponse;
import io.regulyn.identity.dto.TenantPlanLimitsRequest;
import io.regulyn.identity.dto.TenantPlanLimitsResponse;
import io.regulyn.identity.dto.TenantUsageIncrementRequest;
import io.regulyn.identity.dto.TenantUsageIncrementResponse;
import io.regulyn.identity.entity.Role;
import io.regulyn.identity.entity.Tenant;
import io.regulyn.identity.entity.User;
import io.regulyn.identity.entity.UserRole;
import io.regulyn.identity.repository.RoleRepository;
import io.regulyn.identity.repository.TenantRepository;
import io.regulyn.identity.repository.UserRepository;
import io.regulyn.identity.repository.UserRoleRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class TenantReadOnlyAndAuditViewIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("identity")
            .withUsername("test")
            .withPassword("test");

    static WireMockServer wireMockServer = new WireMockServer(0);

    static {
        wireMockServer.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "identity");
        registry.add("spring.flyway.schemas", () -> "identity");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("audit.schema", () -> "identity");
        registry.add("regulyn.outbox.relay.enabled", () -> "false");
        registry.add("evidence.service.url", () -> wireMockServer.baseUrl());
        registry.add("invite.token.secret", () -> "test-secret");
        registry.add("invite.token.encryption-key", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        registry.add("api.key.hmac-secret", () -> "test-api-key-secret");
        registry.add("internal.auth.token", () -> "internal-test");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void resetWireMock() {
        restTemplate.getRestTemplate().setRequestFactory(
                new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory())
        );
        wireMockServer.resetAll();
        wireMockServer.stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"evidenceId\":\"" + UUID.randomUUID() + "\",\"status\":\"CREATED\"}")));

        jdbcTemplate.update("delete from identity.user_roles");
        jdbcTemplate.update("delete from identity.api_keys");
        jdbcTemplate.update("delete from identity.user_invites");
        jdbcTemplate.update("delete from identity.tenant_feature_flags");
        jdbcTemplate.update("delete from identity.tenant_monthly_usage");
        jdbcTemplate.update("delete from identity.tenant_plan_limits");
        jdbcTemplate.update("delete from identity.idempotency_keys");
        jdbcTemplate.update("delete from identity.audit_events");
        jdbcTemplate.update("delete from identity.outbox_events");
        jdbcTemplate.update("delete from identity.users");
        jdbcTemplate.update("delete from identity.roles");
        jdbcTemplate.update("delete from identity.tenants");
    }

    @Test
    void plan_limits_update_writes_audit_and_outbox() throws Exception {
        UUID tenantId = seedActiveTenant();
        Role adminRole = seedRole(tenantId, "TENANT_ADMIN");
        User admin = seedUser(tenantId, "planadmin@example.com", "Admin123!", true);
        assignRole(tenantId, admin, adminRole);

        String token = login(admin.getEmail(), "Admin123!");

        TenantPlanLimitsRequest request = new TenantPlanLimitsRequest();
        request.setMaxUsers(10);
        request.setDsarPerMonth(3);
        request.setExportsPerMonth(4);

        ResponseEntity<TenantPlanLimitsResponse> response = restTemplate.exchange(
                "/tenants/plan-limits",
                HttpMethod.PUT,
                new HttpEntity<>(request, headersForTenant(tenantId, admin.getUserId(), token)),
                TenantPlanLimitsResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        Integer maxUsers = jdbcTemplate.queryForObject(
                "select max_users from identity.tenant_plan_limits where tenant_id = ?",
                Integer.class,
                tenantId
        );
        assertThat(maxUsers).isEqualTo(10);

        assertThat(countAudit("TENANT_PLAN_LIMITS_UPDATED", tenantId)).isEqualTo(1L);
        assertThat(countOutbox("TENANT_PLAN_LIMITS_UPDATED", tenantId)).isEqualTo(1L);
    }

    @Test
    void plan_limits_update_fails_when_evidence_unavailable() throws Exception {
        UUID tenantId = seedActiveTenant();
        Role adminRole = seedRole(tenantId, "TENANT_ADMIN");
        User admin = seedUser(tenantId, "planadmin2@example.com", "Admin123!", true);
        assignRole(tenantId, admin, adminRole);

        String token = login(admin.getEmail(), "Admin123!");

        wireMockServer.stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse().withStatus(503)));

        TenantPlanLimitsRequest request = new TenantPlanLimitsRequest();
        request.setMaxUsers(8);

        ResponseEntity<String> response = restTemplate.exchange(
                "/tenants/plan-limits",
                HttpMethod.PUT,
                new HttpEntity<>(request, headersForTenant(tenantId, admin.getUserId(), token)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(readErrorMessage(response)).isEqualTo("EVIDENCE_UNAVAILABLE");

        Long planCount = jdbcTemplate.queryForObject(
                "select count(*) from identity.tenant_plan_limits where tenant_id = ?",
                Long.class,
                tenantId
        );
        assertThat(planCount).isEqualTo(0L);

        assertThat(countAudit("TENANT_PLAN_LIMITS_UPDATED", tenantId)).isEqualTo(0L);
        assertThat(countOutbox("TENANT_PLAN_LIMITS_UPDATED", tenantId)).isEqualTo(0L);
    }

        @Test
        void usage_increment_is_idempotent_and_rejects_conflicts() throws Exception {
        UUID tenantId = seedActiveTenant();
        Role adminRole = seedRole(tenantId, "TENANT_ADMIN");
        User admin = seedUser(tenantId, "usageadmin@example.com", "Admin123!", true);
        assignRole(tenantId, admin, adminRole);

        TenantUsageIncrementRequest request = new TenantUsageIncrementRequest();
        request.setDsarIncrement(1);

        ResponseEntity<TenantUsageIncrementResponse> firstResponse = restTemplate.exchange(
            "/internal/tenants/" + tenantId + "/usage/increment",
            HttpMethod.POST,
            new HttpEntity<>(request, internalHeaders("usage-1")),
            TenantUsageIncrementResponse.class
        );
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(firstResponse.getBody().getDsarCount()).isEqualTo(1);

        ResponseEntity<TenantUsageIncrementResponse> secondResponse = restTemplate.exchange(
            "/internal/tenants/" + tenantId + "/usage/increment",
            HttpMethod.POST,
            new HttpEntity<>(request, internalHeaders("usage-1")),
            TenantUsageIncrementResponse.class
        );
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody().getDsarCount()).isEqualTo(1);

        Integer dsarCount = jdbcTemplate.queryForObject(
            "select dsar_count from identity.tenant_monthly_usage where tenant_id = ?",
            Integer.class,
            tenantId
        );
        assertThat(dsarCount).isEqualTo(1);

        TenantUsageIncrementRequest conflictRequest = new TenantUsageIncrementRequest();
        conflictRequest.setDsarIncrement(2);

        ResponseEntity<String> conflictResponse = restTemplate.exchange(
            "/internal/tenants/" + tenantId + "/usage/increment",
            HttpMethod.POST,
            new HttpEntity<>(conflictRequest, internalHeaders("usage-1")),
            String.class
        );
        assertThat(conflictResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readErrorMessage(conflictResponse)).isEqualTo("IDEMPOTENCY_KEY_REUSED_DIFFERENT_REQUEST");
        }

        @Test
        void read_only_blocks_create_ops_after_usage_increment() throws Exception {
        UUID tenantId = seedActiveTenant();
        Role adminRole = seedRole(tenantId, "TENANT_ADMIN");
        Role employeeRole = seedRole(tenantId, "EMPLOYEE");
        User admin = seedUser(tenantId, "readonlyadmin@example.com", "Admin123!", true);
        assignRole(tenantId, admin, adminRole);

        String token = login(admin.getEmail(), "Admin123!");

        ApiKeyCreateResponse apiKeyResponse = restTemplate.exchange(
            "/api-keys",
            HttpMethod.POST,
            new HttpEntity<>(new ApiKeyCreateRequest(), headersForTenant(tenantId, admin.getUserId(), token)),
            ApiKeyCreateResponse.class
        ).getBody();
        assertThat(apiKeyResponse).isNotNull();

        TenantPlanLimitsRequest limitsRequest = new TenantPlanLimitsRequest();
        limitsRequest.setDsarPerMonth(1);
        ResponseEntity<TenantPlanLimitsResponse> limitsResponse = restTemplate.exchange(
            "/tenants/plan-limits",
            HttpMethod.PUT,
            new HttpEntity<>(limitsRequest, headersForTenant(tenantId, admin.getUserId(), token)),
            TenantPlanLimitsResponse.class
        );
        assertThat(limitsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        TenantUsageIncrementRequest incrementRequest = new TenantUsageIncrementRequest();
        incrementRequest.setDsarIncrement(1);
        ResponseEntity<TenantUsageIncrementResponse> incrementResponse = restTemplate.exchange(
            "/internal/tenants/" + tenantId + "/usage/increment",
            HttpMethod.POST,
            new HttpEntity<>(incrementRequest, internalHeaders("usage-readonly")),
            TenantUsageIncrementResponse.class
        );
        assertThat(incrementResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Boolean readOnly = jdbcTemplate.queryForObject(
            "select read_only from identity.tenants where tenant_id = ?",
            Boolean.class,
            tenantId
        );
        assertThat(readOnly).isTrue();

        CreateUserRequest createUserRequest = new CreateUserRequest();
        createUserRequest.setEmail("blocked@example.com");
        createUserRequest.setPassword("Passw0rd!");
        createUserRequest.setFirstName("Blocked");
        createUserRequest.setLastName("User");
        ResponseEntity<String> createUserResponse = restTemplate.exchange(
            "/users",
            HttpMethod.POST,
            new HttpEntity<>(createUserRequest, headersForTenant(tenantId, admin.getUserId(), token)),
            String.class
        );
        assertThat(createUserResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readErrorMessage(createUserResponse)).isEqualTo("LIMIT_DSAR_EXCEEDED");

        CreateInviteRequest inviteRequest = new CreateInviteRequest();
        inviteRequest.setEmail("invitee@example.com");
        inviteRequest.setRoles(java.util.List.of(employeeRole.getRoleName()));
        ResponseEntity<String> inviteResponse = restTemplate.exchange(
            "/users/invites",
            HttpMethod.POST,
            new HttpEntity<>(inviteRequest, headersForTenant(tenantId, admin.getUserId(), token)),
            String.class
        );
        assertThat(inviteResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readErrorMessage(inviteResponse)).isEqualTo("LIMIT_DSAR_EXCEEDED");

        ResponseEntity<String> apiKeyCreateResponse = restTemplate.exchange(
            "/api-keys",
            HttpMethod.POST,
            new HttpEntity<>(new ApiKeyCreateRequest(), headersForTenant(tenantId, admin.getUserId(), token)),
            String.class
        );
        assertThat(apiKeyCreateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readErrorMessage(apiKeyCreateResponse)).isEqualTo("LIMIT_DSAR_EXCEEDED");

        ResponseEntity<String> rotateResponse = restTemplate.exchange(
            "/api-keys/" + apiKeyResponse.getApiKeyId() + "/rotate",
            HttpMethod.POST,
            new HttpEntity<>(new ApiKeyCreateRequest(), headersForTenant(tenantId, admin.getUserId(), token)),
            String.class
        );
        assertThat(rotateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(readErrorMessage(rotateResponse)).isEqualTo("LIMIT_DSAR_EXCEEDED");
        }

        @Test
        void audit_query_filters_pagination_and_tenant_isolation() throws Exception {
        UUID tenantId = seedActiveTenant();
        Role adminRole = seedRole(tenantId, "TENANT_ADMIN");
        User admin = seedUser(tenantId, "auditadmin@example.com", "Admin123!", true);
        assignRole(tenantId, admin, adminRole);

        String token = login(admin.getEmail(), "Admin123!");

        Instant from = Instant.now().minusSeconds(5);

        TenantPlanLimitsRequest limitsRequest = new TenantPlanLimitsRequest();
        limitsRequest.setMaxUsers(7);
        restTemplate.exchange(
            "/tenants/plan-limits",
            HttpMethod.PUT,
            new HttpEntity<>(limitsRequest, headersForTenant(tenantId, admin.getUserId(), token)),
            TenantPlanLimitsResponse.class
        );

        restTemplate.exchange(
            "/api-keys",
            HttpMethod.POST,
            new HttpEntity<>(new ApiKeyCreateRequest(), headersForTenant(tenantId, admin.getUserId(), token)),
            ApiKeyCreateResponse.class
        );

        Instant to = Instant.now().plusSeconds(5);

        UUID otherTenantId = seedActiveTenant();
        Role otherAdminRole = seedRole(otherTenantId, "TENANT_ADMIN");
        User otherAdmin = seedUser(otherTenantId, "auditadmin2@example.com", "Admin123!", true);
        assignRole(otherTenantId, otherAdmin, otherAdminRole);
        String otherToken = login(otherAdmin.getEmail(), "Admin123!");
        restTemplate.exchange(
            "/tenants/plan-limits",
            HttpMethod.PUT,
            new HttpEntity<>(limitsRequest, headersForTenant(otherTenantId, otherAdmin.getUserId(), otherToken)),
            TenantPlanLimitsResponse.class
        );

        ResponseEntity<AuditEventPageResponse> byUser = restTemplate.exchange(
            "/admin/audit-events?userId=" + admin.getUserId(),
            HttpMethod.GET,
            new HttpEntity<>(headersForTenant(tenantId, admin.getUserId(), token)),
            AuditEventPageResponse.class
        );
        assertThat(byUser.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byUser.getBody()).isNotNull();
        assertThat(byUser.getBody().getItems()).isNotEmpty();
        assertThat(byUser.getBody().getItems()).allMatch(item -> tenantId.equals(item.getTenantId()));

        ResponseEntity<AuditEventPageResponse> byEventType = restTemplate.exchange(
            "/admin/audit-events?eventType=TENANT_PLAN_LIMITS_UPDATED",
            HttpMethod.GET,
            new HttpEntity<>(headersForTenant(tenantId, admin.getUserId(), token)),
            AuditEventPageResponse.class
        );
        assertThat(byEventType.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byEventType.getBody()).isNotNull();
        assertThat(byEventType.getBody().getItems()).allMatch(item -> "TENANT_PLAN_LIMITS_UPDATED".equals(item.getAction()));

        ResponseEntity<AuditEventPageResponse> byTime = restTemplate.exchange(
            "/admin/audit-events?from=" + from.toString() + "&to=" + to.toString(),
            HttpMethod.GET,
            new HttpEntity<>(headersForTenant(tenantId, admin.getUserId(), token)),
            AuditEventPageResponse.class
        );
        assertThat(byTime.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byTime.getBody()).isNotNull();
        assertThat(byTime.getBody().getItems()).isNotEmpty();

        ResponseEntity<AuditEventPageResponse> page0 = restTemplate.exchange(
            "/admin/audit-events?page=0&size=1",
            HttpMethod.GET,
            new HttpEntity<>(headersForTenant(tenantId, admin.getUserId(), token)),
            AuditEventPageResponse.class
        );
        ResponseEntity<AuditEventPageResponse> page1 = restTemplate.exchange(
            "/admin/audit-events?page=1&size=1",
            HttpMethod.GET,
            new HttpEntity<>(headersForTenant(tenantId, admin.getUserId(), token)),
            AuditEventPageResponse.class
        );
        assertThat(page0.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page0.getBody()).isNotNull();
        assertThat(page1.getBody()).isNotNull();
        assertThat(page0.getBody().getItems()).hasSize(1);
        assertThat(page1.getBody().getItems()).hasSize(1);
        assertThat(page0.getBody().getTotal()).isGreaterThanOrEqualTo(2);
        assertThat(page1.getBody().getTotal()).isEqualTo(page0.getBody().getTotal());
        }

    @Test
    void audit_query_uses_index_when_seqscan_disabled() throws Exception {
        UUID tenantId = seedActiveTenant();
        Role adminRole = seedRole(tenantId, "TENANT_ADMIN");
        User admin = seedUser(tenantId, "auditindex@example.com", "Admin123!", true);
        assignRole(tenantId, admin, adminRole);

        String token = login(admin.getEmail(), "Admin123!");

        TenantPlanLimitsRequest limitsRequest = new TenantPlanLimitsRequest();
        limitsRequest.setMaxUsers(6);
        restTemplate.exchange(
                "/tenants/plan-limits",
                HttpMethod.PUT,
                new HttpEntity<>(limitsRequest, headersForTenant(tenantId, admin.getUserId(), token)),
                TenantPlanLimitsResponse.class
        );

        jdbcTemplate.execute("set enable_seqscan = off");
        java.util.List<String> plan = jdbcTemplate.queryForList(
                "explain (format text) select * from identity.audit_events where tenant_id = ? order by occurred_at desc limit 5",
                String.class,
                tenantId
        );

        String planText = String.join("\n", plan);
        boolean usesIndex = planText.contains("Index Scan")
            || planText.contains("Index Only Scan")
            || planText.contains("Bitmap Index Scan");
        assertThat(usesIndex).isTrue();
    }

        @Test
        void feature_flags_update_and_tenant_isolation() throws Exception {
        UUID tenantId = seedActiveTenant();
        Role adminRole = seedRole(tenantId, "TENANT_ADMIN");
        User admin = seedUser(tenantId, "flagadmin@example.com", "Admin123!", true);
        assignRole(tenantId, admin, adminRole);

        String token = login(admin.getEmail(), "Admin123!");

        TenantFeatureFlagRequest request = new TenantFeatureFlagRequest();
        request.setEnabled(true);
        request.setValue(objectMapper.readTree("{\"level\":\"beta\"}"));

        ResponseEntity<TenantFeatureFlagResponse> updateResponse = restTemplate.exchange(
            "/tenants/feature-flags/feature-x",
            HttpMethod.PUT,
            new HttpEntity<>(request, headersForTenant(tenantId, admin.getUserId(), token)),
            TenantFeatureFlagResponse.class
        );
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).isNotNull();
        assertThat(updateResponse.getBody().getFlagKey()).isEqualTo("feature-x");

        assertThat(countAudit("TENANT_FEATURE_FLAG_UPDATED", tenantId)).isEqualTo(1L);
        assertThat(countOutbox("TENANT_FEATURE_FLAG_UPDATED", tenantId)).isEqualTo(1L);

        ResponseEntity<TenantFeatureFlagResponse[]> listResponse = restTemplate.exchange(
            "/tenants/feature-flags",
            HttpMethod.GET,
            new HttpEntity<>(headersForTenant(tenantId, admin.getUserId(), token)),
            TenantFeatureFlagResponse[].class
        );
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull();
        assertThat(listResponse.getBody().length).isEqualTo(1);

        UUID otherTenantId = seedActiveTenant();
        Role otherAdminRole = seedRole(otherTenantId, "TENANT_ADMIN");
        User otherAdmin = seedUser(otherTenantId, "flagadmin2@example.com", "Admin123!", true);
        assignRole(otherTenantId, otherAdmin, otherAdminRole);
        String otherToken = login(otherAdmin.getEmail(), "Admin123!");

        ResponseEntity<TenantFeatureFlagResponse[]> otherList = restTemplate.exchange(
            "/tenants/feature-flags",
            HttpMethod.GET,
            new HttpEntity<>(headersForTenant(otherTenantId, otherAdmin.getUserId(), otherToken)),
            TenantFeatureFlagResponse[].class
        );
        assertThat(otherList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(otherList.getBody()).isNotNull();
        assertThat(otherList.getBody().length).isEqualTo(0);
        }

        @Test
        void feature_flags_update_fails_when_evidence_unavailable() throws Exception {
        UUID tenantId = seedActiveTenant();
        Role adminRole = seedRole(tenantId, "TENANT_ADMIN");
        User admin = seedUser(tenantId, "flagadmin3@example.com", "Admin123!", true);
        assignRole(tenantId, admin, adminRole);

        String token = login(admin.getEmail(), "Admin123!");

        wireMockServer.stubFor(post(urlEqualTo("/evidence"))
            .willReturn(aResponse().withStatus(503)));

        TenantFeatureFlagRequest request = new TenantFeatureFlagRequest();
        request.setEnabled(true);

        ResponseEntity<String> response = restTemplate.exchange(
            "/tenants/feature-flags/feature-y",
            HttpMethod.PUT,
            new HttpEntity<>(request, headersForTenant(tenantId, admin.getUserId(), token)),
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(readErrorMessage(response)).isEqualTo("EVIDENCE_UNAVAILABLE");

        Long flagCount = jdbcTemplate.queryForObject(
            "select count(*) from identity.tenant_feature_flags where tenant_id = ?",
            Long.class,
            tenantId
        );
        assertThat(flagCount).isEqualTo(0L);

        assertThat(countAudit("TENANT_FEATURE_FLAG_UPDATED", tenantId)).isEqualTo(0L);
        assertThat(countOutbox("TENANT_FEATURE_FLAG_UPDATED", tenantId)).isEqualTo(0L);
        }

    private String login(String email, String password) throws Exception {
        io.regulyn.identity.dto.LoginRequest loginRequest = new io.regulyn.identity.dto.LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword(password);
        ResponseEntity<io.regulyn.identity.dto.LoginResponse> response = restTemplate.postForEntity(
                "/auth/login",
                loginRequest,
                io.regulyn.identity.dto.LoginResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().getToken();
    }

    private HttpHeaders headersForTenant(UUID tenantId, UUID actorId, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", tenantId.toString());
        headers.set("X-Actor-Id", actorId != null ? actorId.toString() : UUID.randomUUID().toString());
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders internalHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Auth", "internal-test");
        headers.set("X-Idempotency-Key", idempotencyKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private UUID seedActiveTenant() {
        Tenant tenant = new Tenant();
        tenant.setName("Part5 Tenant");
        tenant.setStatus(TenantStatuses.ACTIVE);
        tenantRepository.save(tenant);
        return tenant.getTenantId();
    }

    private Role seedRole(UUID tenantId, String roleName) {
        return roleRepository.findByTenantIdAndRoleName(tenantId, roleName)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setTenantId(tenantId);
                    role.setRoleName(roleName);
                    role.setDescription(roleName + " role");
                    return roleRepository.save(role);
                });
    }

    private User seedUser(UUID tenantId, String email, String password, boolean enabled) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEnabled(enabled);
        return userRepository.save(user);
    }

    private void assignRole(UUID tenantId, User user, Role role) {
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getUserId());
        userRole.setRoleId(role.getRoleId());
        userRole.setTenantId(tenantId);
        userRole.setAssignedBy(user.getUserId());
        userRoleRepository.save(userRole);
    }

    private long countAudit(String action, UUID tenantId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from identity.audit_events where tenant_id = ? and action = ?",
                Long.class,
                tenantId,
                action
        );
        return count != null ? count : 0L;
    }

    private long countOutbox(String eventType, UUID tenantId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from identity.outbox_events where tenant_id = ? and event_type = ?",
                Long.class,
                tenantId,
                eventType
        );
        return count != null ? count : 0L;
    }

    private String readErrorMessage(ResponseEntity<String> response) throws Exception {
        JsonNode node = objectMapper.readTree(response.getBody());
        return node.get("message").asText();
    }
}