package io.regulyn.identity;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.regulyn.identity.constants.TenantStatuses;
import io.regulyn.identity.dto.*;
import io.regulyn.identity.entity.Role;
import io.regulyn.identity.entity.Tenant;
import io.regulyn.identity.entity.User;
import io.regulyn.identity.entity.UserRole;
import io.regulyn.identity.repository.RoleRepository;
import io.regulyn.identity.repository.TenantRepository;
import io.regulyn.identity.repository.UserRepository;
import io.regulyn.identity.repository.UserRoleRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {IdentityTenantServiceApplication.class},
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
    }
)
@Testcontainers
@ActiveProfiles("test")
class TenantLifecycleIT {

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
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
        wireMockServer.stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"evidenceId\":\"" + UUID.randomUUID() + "\",\"status\":\"CREATED\"}")));
    }

    @Test
    void activate_valid_transition() {
        TenantResponse created = createTenant("Activate Tenant");

        ResponseEntity<TenantResponse> response = restTemplate.exchange(
                "/tenants/" + created.getTenantId() + "/activate",
                HttpMethod.POST,
                new HttpEntity<>(headersForTenant(created.getTenantId())),
                TenantResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(TenantStatuses.ACTIVE);
        assertThat(response.getBody().getActivatedAt()).isNotNull();

        assertThat(countAudit("TENANT_ACTIVATED", created.getTenantId())).isEqualTo(1);
        assertThat(countOutbox("TENANT_ACTIVATED", created.getTenantId())).isEqualTo(1);
    }

    @Test
    void resume_invalid_transition_blocked() {
        TenantResponse created = createTenant("Resume Invalid");

        ResponseEntity<String> response = restTemplate.exchange(
                "/tenants/" + created.getTenantId() + "/resume",
                HttpMethod.POST,
                new HttpEntity<>(headersForTenant(created.getTenantId())),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void bootstrap_admin_only_once() {
        TenantResponse created = createTenant("Bootstrap Tenant");
        Role adminRole = seedRole(created.getTenantId(), "TENANT_ADMIN");

        BootstrapAdminRequest request = new BootstrapAdminRequest();
        request.setEmail("admin@tenant.test");
        request.setPassword("Passw0rd!");
        request.setFirstName("Admin");
        request.setLastName("User");

        ResponseEntity<BootstrapAdminResponse> first = restTemplate.exchange(
                "/tenants/" + created.getTenantId() + "/bootstrap-admin",
                HttpMethod.POST,
                new HttpEntity<>(request, headersForTenant(created.getTenantId())),
                BootstrapAdminResponse.class
        );

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID adminId = first.getBody().getUserId();

        ResponseEntity<BootstrapAdminResponse> second = restTemplate.exchange(
                "/tenants/" + created.getTenantId() + "/bootstrap-admin",
                HttpMethod.POST,
                new HttpEntity<>(request, headersForTenant(created.getTenantId())),
                BootstrapAdminResponse.class
        );

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().getUserId()).isEqualTo(adminId);
        assertThat(userRepository.findByTenantId(created.getTenantId()).size()).isEqualTo(1);
        assertThat(userRoleRepository.findByUserIdAndTenantId(adminId, created.getTenantId()).size()).isEqualTo(1);
    }

    @Test
    void suspend_blocks_auth_login() {
        TenantResponse created = createTenant("Suspend Tenant");
        seedRole(created.getTenantId(), "TENANT_ADMIN");
        User admin = seedUser(created.getTenantId(), "suspend@tenant.test", "Password123");
        assignRole(admin, "TENANT_ADMIN");

        restTemplate.exchange(
                "/tenants/" + created.getTenantId() + "/activate",
                HttpMethod.POST,
                new HttpEntity<>(headersForTenant(created.getTenantId())),
                TenantResponse.class
        );

        restTemplate.exchange(
                "/tenants/" + created.getTenantId() + "/suspend",
                HttpMethod.POST,
                new HttpEntity<>(headersForTenant(created.getTenantId())),
                TenantResponse.class
        );

        LoginRequest login = new LoginRequest(admin.getEmail(), "Password123");
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/login", login, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void delete_guarded_by_compliance_hold() {
        TenantResponse created = createTenant("Delete Guarded");

        restTemplate.exchange(
                "/tenants/" + created.getTenantId() + "/delete-request",
                HttpMethod.POST,
                new HttpEntity<>(new DeleteTenantRequest(), headersForTenant(created.getTenantId())),
                TenantResponse.class
        );

        jdbcTemplate.update("update identity.tenants set compliance_hold = true where tenant_id = ?", created.getTenantId());

        ResponseEntity<String> response = restTemplate.exchange(
                "/tenants/" + created.getTenantId(),
                HttpMethod.DELETE,
                new HttpEntity<>(new DeleteTenantRequest(), headersForTenant(created.getTenantId())),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(countAudit("TENANT_DELETE_BLOCKED", created.getTenantId())).isEqualTo(1);
        assertThat(countOutbox("TENANT_DELETE_BLOCKED", created.getTenantId())).isEqualTo(1);
    }

    @Test
    void evidence_failure_blocks_transition() {
        TenantResponse created = createTenant("Evidence Fail");

        wireMockServer.stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse().withStatus(503)));

        ResponseEntity<String> response = restTemplate.exchange(
                "/tenants/" + created.getTenantId() + "/activate",
                HttpMethod.POST,
                new HttpEntity<>(headersForTenant(created.getTenantId())),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        Tenant tenant = tenantRepository.findById(created.getTenantId()).orElseThrow();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatuses.DRAFT);
        assertThat(countAudit("TENANT_ACTIVATED", created.getTenantId())).isEqualTo(0);
        assertThat(countOutbox("TENANT_ACTIVATED", created.getTenantId())).isEqualTo(0);
    }

    private TenantResponse createTenant(String name) {
        CreateTenantRequest request = new CreateTenantRequest();
        request.setName(name);
        ResponseEntity<TenantResponse> response = restTemplate.postForEntity("/tenants", request, TenantResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private HttpHeaders headersForTenant(UUID tenantId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", tenantId.toString());
        headers.set("X-Actor-Id", UUID.randomUUID().toString());
        return headers;
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

    private User seedUser(UUID tenantId, String email, String password) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private void assignRole(User user, String roleName) {
        Role role = roleRepository.findByTenantIdAndRoleName(user.getTenantId(), roleName).orElseThrow();
        UserRole assignment = new UserRole();
        assignment.setUserId(user.getUserId());
        assignment.setRoleId(role.getRoleId());
        assignment.setTenantId(user.getTenantId());
        userRoleRepository.save(assignment);
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
}
