package io.regulyn.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.regulyn.identity.constants.TenantStatuses;
import io.regulyn.identity.dto.*;
import io.regulyn.identity.entity.ApiKey;
import io.regulyn.identity.entity.Role;
import io.regulyn.identity.entity.Tenant;
import io.regulyn.identity.entity.User;
import io.regulyn.identity.entity.UserRole;
import io.regulyn.identity.repository.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class UserLockAndApiKeyIT {

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
    private ApiKeyRepository apiKeyRepository;

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
        jdbcTemplate.update("delete from identity.idempotency_keys");
        jdbcTemplate.update("delete from identity.audit_events");
        jdbcTemplate.update("delete from identity.outbox_events");
        jdbcTemplate.update("delete from identity.users");
        jdbcTemplate.update("delete from identity.roles");
        jdbcTemplate.update("delete from identity.tenants");
    }

    @Test
    void lock_user_blocks_login() throws Exception {
        UUID tenantId = seedActiveTenant();
        Role adminRole = seedRole(tenantId, "TENANT_ADMIN");
        Role userRole = seedRole(tenantId, "EMPLOYEE");

        User admin = seedUser(tenantId, "admin@example.com", "Admin123!", true);
        assignRole(tenantId, admin, adminRole);

        User user = seedUser(tenantId, "user1@example.com", "Passw0rd!", true);
        assignRole(tenantId, user, userRole);

        String token = login(admin.getEmail(), "Admin123!");

        LockUserRequest lockRequest = new LockUserRequest();
        lockRequest.setReason("Too many attempts");
        ResponseEntity<LockUserResponse> lockResponse = restTemplate.exchange(
                "/users/" + user.getUserId() + "/lock",
                HttpMethod.POST,
                new HttpEntity<>(lockRequest, headersForTenant(tenantId, admin.getUserId(), token)),
                LockUserResponse.class
        );
        assertThat(lockResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(user.getEmail());
        loginRequest.setPassword("Passw0rd!");
        ResponseEntity<String> loginResponse = restTemplate.postForEntity("/auth/login", loginRequest, String.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(readErrorMessage(loginResponse)).isEqualTo("USER_LOCKED");

        assertThat(countAudit("USER_LOCKED", tenantId)).isEqualTo(1L);
        assertThat(countOutbox("USER_LOCKED", tenantId)).isEqualTo(1L);
    }

    @Test
    void unlock_user_allows_login() throws Exception {
        UUID tenantId = seedActiveTenant();
        Role adminRole = seedRole(tenantId, "TENANT_ADMIN");

        User admin = seedUser(tenantId, "admin2@example.com", "Admin123!", true);
        assignRole(tenantId, admin, adminRole);

        User user = seedUser(tenantId, "user2@example.com", "Passw0rd!", true);

        String token = login(admin.getEmail(), "Admin123!");

        restTemplate.exchange(
                "/users/" + user.getUserId() + "/lock",
                HttpMethod.POST,
                new HttpEntity<>(new LockUserRequest(), headersForTenant(tenantId, admin.getUserId(), token)),
                LockUserResponse.class
        );

        UnlockUserRequest unlockRequest = new UnlockUserRequest();
        unlockRequest.setReason("Resolved");
        ResponseEntity<UnlockUserResponse> unlockResponse = restTemplate.exchange(
                "/users/" + user.getUserId() + "/unlock",
                HttpMethod.POST,
                new HttpEntity<>(unlockRequest, headersForTenant(tenantId, admin.getUserId(), token)),
                UnlockUserResponse.class
        );
        assertThat(unlockResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(user.getEmail());
        loginRequest.setPassword("Passw0rd!");
        ResponseEntity<LoginResponse> loginResponse = restTemplate.postForEntity("/auth/login", loginRequest, LoginResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(countAudit("USER_UNLOCKED", tenantId)).isEqualTo(1L);
        assertThat(countOutbox("USER_UNLOCKED", tenantId)).isEqualTo(1L);
    }

    @Test
    void lock_idempotent_no_double_events() {
        UUID tenantId = seedActiveTenant();
        Role adminRole = seedRole(tenantId, "TENANT_ADMIN");
        User admin = seedUser(tenantId, "admin3@example.com", "Admin123!", true);
        assignRole(tenantId, admin, adminRole);
        User user = seedUser(tenantId, "user3@example.com", "Passw0rd!", true);

        String token = login(admin.getEmail(), "Admin123!");

        restTemplate.exchange(
                "/users/" + user.getUserId() + "/lock",
                HttpMethod.POST,
                new HttpEntity<>(new LockUserRequest(), headersForTenant(tenantId, admin.getUserId(), token)),
                LockUserResponse.class
        );
        restTemplate.exchange(
                "/users/" + user.getUserId() + "/lock",
                HttpMethod.POST,
                new HttpEntity<>(new LockUserRequest(), headersForTenant(tenantId, admin.getUserId(), token)),
                LockUserResponse.class
        );

        assertThat(countAudit("USER_LOCKED", tenantId)).isEqualTo(1L);
        assertThat(countOutbox("USER_LOCKED", tenantId)).isEqualTo(1L);
    }

    @Test
    void api_key_create_and_validate() {
        UUID tenantId = seedActiveTenant();
        Role adminRole = seedRole(tenantId, "TENANT_ADMIN");
        User admin = seedUser(tenantId, "admin4@example.com", "Admin123!", true);
        assignRole(tenantId, admin, adminRole);

        String token = login(admin.getEmail(), "Admin123!");

        ApiKeyCreateRequest request = new ApiKeyCreateRequest();
        request.setName("connector");
        request.setExpiresInDays(30);

        ResponseEntity<ApiKeyCreateResponse> response = restTemplate.exchange(
                "/api-keys",
                HttpMethod.POST,
                new HttpEntity<>(request, headersForTenant(tenantId, admin.getUserId(), token)),
                ApiKeyCreateResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiKeyCreateResponse body = response.getBody();
        assertThat(body.getApiKey()).isNotBlank();
        assertThat(body.getPrefix()).isNotBlank();

        ResponseEntity<ValidateApiKeyResponse> validateResponse = restTemplate.exchange(
                "/internal/api-keys/validate",
                HttpMethod.POST,
                new HttpEntity<>(new ValidateApiKeyRequest(body.getApiKey()), internalHeaders()),
                ValidateApiKeyResponse.class
        );
        assertThat(validateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(validateResponse.getBody().isValid()).isTrue();

        ApiKey stored = apiKeyRepository.findById(body.getApiKeyId()).orElseThrow();
        assertThat(stored.getLastUsedAt()).isNotNull();

        assertThat(countAudit("API_KEY_CREATED", tenantId)).isEqualTo(1L);
        assertThat(countOutbox("API_KEY_CREATED", tenantId)).isEqualTo(1L);
    }

    @Test
    void api_key_revoke_blocks_validate() {
        UUID tenantId = seedActiveTenant();
        Role adminRole = seedRole(tenantId, "TENANT_ADMIN");
        User admin = seedUser(tenantId, "admin5@example.com", "Admin123!", true);
        assignRole(tenantId, admin, adminRole);

        String token = login(admin.getEmail(), "Admin123!");

        ApiKeyCreateResponse created = createApiKey(tenantId, admin, token);

        ApiKeyRevokeRequest revokeRequest = new ApiKeyRevokeRequest();
        revokeRequest.setReason("rotation");
        ResponseEntity<ApiKeyRevokeResponse> revokeResponse = restTemplate.exchange(
                "/api-keys/" + created.getApiKeyId() + "/revoke",
                HttpMethod.POST,
                new HttpEntity<>(revokeRequest, headersForTenant(tenantId, admin.getUserId(), token)),
                ApiKeyRevokeResponse.class
        );
        assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ValidateApiKeyResponse> validateResponse = restTemplate.exchange(
                "/internal/api-keys/validate",
                HttpMethod.POST,
                new HttpEntity<>(new ValidateApiKeyRequest(created.getApiKey()), internalHeaders()),
                ValidateApiKeyResponse.class
        );
        assertThat(validateResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(countAudit("API_KEY_REVOKED", tenantId)).isEqualTo(1L);
        assertThat(countOutbox("API_KEY_REVOKED", tenantId)).isEqualTo(1L);
    }

    @Test
    void api_key_rotate_invalidates_old_immediately() {
        UUID tenantId = seedActiveTenant();
        Role adminRole = seedRole(tenantId, "TENANT_ADMIN");
        User admin = seedUser(tenantId, "admin6@example.com", "Admin123!", true);
        assignRole(tenantId, admin, adminRole);

        String token = login(admin.getEmail(), "Admin123!");

        ApiKeyCreateResponse created = createApiKey(tenantId, admin, token);

        ResponseEntity<ApiKeyRotateResponse> rotateResponse = restTemplate.exchange(
                "/api-keys/" + created.getApiKeyId() + "/rotate",
                HttpMethod.POST,
                new HttpEntity<>(new ApiKeyCreateRequest(), headersForTenant(tenantId, admin.getUserId(), token)),
                ApiKeyRotateResponse.class
        );
        assertThat(rotateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiKeyRotateResponse rotated = rotateResponse.getBody();

        ResponseEntity<ValidateApiKeyResponse> oldValidate = restTemplate.exchange(
                "/internal/api-keys/validate",
                HttpMethod.POST,
                new HttpEntity<>(new ValidateApiKeyRequest(created.getApiKey()), internalHeaders()),
                ValidateApiKeyResponse.class
        );
        assertThat(oldValidate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<ValidateApiKeyResponse> newValidate = restTemplate.exchange(
                "/internal/api-keys/validate",
                HttpMethod.POST,
                new HttpEntity<>(new ValidateApiKeyRequest(rotated.getApiKey()), internalHeaders()),
                ValidateApiKeyResponse.class
        );
        assertThat(newValidate.getStatusCode()).isEqualTo(HttpStatus.OK);

        ApiKey oldKey = apiKeyRepository.findById(created.getApiKeyId()).orElseThrow();
        ApiKey newKey = apiKeyRepository.findById(rotated.getApiKeyId()).orElseThrow();

        assertThat(oldKey.getRevokedAt()).isNotNull();
        assertThat(oldKey.getEnabled()).isFalse();
        assertThat(newKey.getRotatedFromApiKeyId()).isEqualTo(oldKey.getApiKeyId());
        assertThat(newKey.getKeyVersion()).isEqualTo(oldKey.getKeyVersion() + 1);

        assertThat(countAudit("API_KEY_ROTATED", tenantId)).isEqualTo(1L);
        assertThat(countOutbox("API_KEY_ROTATED", tenantId)).isEqualTo(1L);
    }

    @Test
    void evidence_failure_blocks_lock_and_api_key_ops() {
        UUID tenantId = seedActiveTenant();
        Role adminRole = seedRole(tenantId, "TENANT_ADMIN");
        User admin = seedUser(tenantId, "admin7@example.com", "Admin123!", true);
        assignRole(tenantId, admin, adminRole);
        User user = seedUser(tenantId, "user7@example.com", "Passw0rd!", true);

        String token = login(admin.getEmail(), "Admin123!");

        wireMockServer.stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse().withStatus(503)));

        ResponseEntity<String> lockResponse = restTemplate.exchange(
                "/users/" + user.getUserId() + "/lock",
                HttpMethod.POST,
                new HttpEntity<>(new LockUserRequest(), headersForTenant(tenantId, admin.getUserId(), token)),
                String.class
        );
        assertThat(lockResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        User reloaded = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(reloaded.getLockedAt()).isNull();

        ResponseEntity<String> createResponse = restTemplate.exchange(
                "/api-keys",
                HttpMethod.POST,
                new HttpEntity<>(new ApiKeyCreateRequest(), headersForTenant(tenantId, admin.getUserId(), token)),
                String.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(apiKeyRepository.findAll()).isEmpty();
        assertThat(countAudit("API_KEY_CREATED", tenantId)).isEqualTo(0L);
        assertThat(countOutbox("API_KEY_CREATED", tenantId)).isEqualTo(0L);
    }

    @Test
    void tenant_status_gating() {
        UUID tenantId = seedActiveTenant();
        Role adminRole = seedRole(tenantId, "TENANT_ADMIN");
        User admin = seedUser(tenantId, "admin8@example.com", "Admin123!", true);
        assignRole(tenantId, admin, adminRole);
        User user = seedUser(tenantId, "user8@example.com", "Passw0rd!", true);

        String token = login(admin.getEmail(), "Admin123!");

        jdbcTemplate.update("update identity.tenants set status = ? where tenant_id = ?", TenantStatuses.SUSPENDED, tenantId);

        ResponseEntity<String> lockResponse = restTemplate.exchange(
                "/users/" + user.getUserId() + "/lock",
                HttpMethod.POST,
                new HttpEntity<>(new LockUserRequest(), headersForTenant(tenantId, admin.getUserId(), token)),
                String.class
        );
        assertThat(lockResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> createResponse = restTemplate.exchange(
                "/api-keys",
                HttpMethod.POST,
                new HttpEntity<>(new ApiKeyCreateRequest(), headersForTenant(tenantId, admin.getUserId(), token)),
                String.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private ApiKeyCreateResponse createApiKey(UUID tenantId, User admin, String token) {
        ResponseEntity<ApiKeyCreateResponse> response = restTemplate.exchange(
                "/api-keys",
                HttpMethod.POST,
                new HttpEntity<>(new ApiKeyCreateRequest(), headersForTenant(tenantId, admin.getUserId(), token)),
                ApiKeyCreateResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private String login(String email, String password) {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword(password);
        ResponseEntity<LoginResponse> response = restTemplate.postForEntity("/auth/login", loginRequest, LoginResponse.class);
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

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Auth", "internal-test");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private UUID seedActiveTenant() {
        Tenant tenant = new Tenant();
        tenant.setName("Part4 Tenant");
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
