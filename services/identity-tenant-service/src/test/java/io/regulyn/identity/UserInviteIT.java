package io.regulyn.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.regulyn.identity.constants.TenantStatuses;
import io.regulyn.identity.dto.AcceptInviteRequest;
import io.regulyn.identity.dto.AcceptInviteResponse;
import io.regulyn.identity.dto.CreateInviteRequest;
import io.regulyn.identity.dto.InviteResponse;
import io.regulyn.identity.entity.Role;
import io.regulyn.identity.entity.Tenant;
import io.regulyn.identity.entity.UserInvite;
import io.regulyn.identity.repository.RoleRepository;
import io.regulyn.identity.repository.TenantRepository;
import io.regulyn.identity.repository.UserInviteRepository;
import io.regulyn.identity.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
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
class UserInviteIT {

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
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserInviteRepository userInviteRepository;

    @Autowired
    private UserRepository userRepository;

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
        wireMockServer.resetAll();
        wireMockServer.stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"evidenceId\":\"" + UUID.randomUUID() + "\",\"status\":\"CREATED\"}")));

        jdbcTemplate.update("delete from identity.user_roles");
        jdbcTemplate.update("delete from identity.user_invites");
        jdbcTemplate.update("delete from identity.api_keys");
        jdbcTemplate.update("delete from identity.idempotency_keys");
        jdbcTemplate.update("delete from identity.audit_events");
        jdbcTemplate.update("delete from identity.outbox_events");
        jdbcTemplate.update("delete from identity.users");
        jdbcTemplate.update("delete from identity.roles");
        jdbcTemplate.update("delete from identity.tenants");
    }

    @Test
    void create_invite_stores_hashed_token_only() throws Exception {
        UUID tenantId = seedActiveTenant();
        seedRole(tenantId, "TENANT_ADMIN");
        seedRole(tenantId, "DPO");

        InviteResponse response = createInvite(tenantId, "user@example.com", List.of("DPO"), null, null);
        assertThat(response.getToken()).isNotBlank();

        UserInvite invite = userInviteRepository.findById(response.getInviteId()).orElseThrow();
        assertThat(invite.getTokenHash()).isNotBlank();
        assertThat(invite.getTokenHash()).isNotEqualTo(response.getToken());
        assertThat(invite.getTokenHashAlg()).isEqualTo("HMAC_SHA256");

        assertThat(countAudit("USER_INVITED", tenantId)).isEqualTo(1L);
        assertThat(countOutbox("USER_INVITED", tenantId)).isEqualTo(1L);

        JsonNode auditMeta = readAuditMetadata("USER_INVITED", tenantId);
        assertThat(auditMeta.get("evidenceId").asText()).isNotBlank();
    }

    @Test
    void create_invite_idempotency_same_key_same_request_returns_same_token() {
        UUID tenantId = seedActiveTenant();
        seedRole(tenantId, "TENANT_ADMIN");
        seedRole(tenantId, "DPO");

        InviteResponse first = createInvite(tenantId, "user@example.com", List.of("DPO"), 1440, "idem-key-1");
        InviteResponse second = createInvite(tenantId, "user@example.com", List.of("DPO"), 1440, "idem-key-1");

        assertThat(second.getInviteId()).isEqualTo(first.getInviteId());
        assertThat(second.getToken()).isEqualTo(first.getToken());
        assertThat(userInviteRepository.findAll().size()).isEqualTo(1);
        assertThat(countAudit("USER_INVITED", tenantId)).isEqualTo(1L);
        assertThat(countOutbox("USER_INVITED", tenantId)).isEqualTo(1L);
    }

    @Test
    void create_invite_idempotency_expired_treated_as_miss() {
        UUID tenantId = seedActiveTenant();
        seedRole(tenantId, "TENANT_ADMIN");
        seedRole(tenantId, "DPO");

        InviteResponse first = createInvite(tenantId, "ttl@example.com", List.of("DPO"), 1440, "idem-ttl");

        jdbcTemplate.update("update identity.idempotency_keys set expires_at = now() - interval '1 minute' where tenant_id = ? and scope = ? and idempotency_key = ?",
                tenantId, "POST:/users/invites", "idem-ttl");
        jdbcTemplate.update("update identity.user_invites set used_at = now() where invite_id = ?", first.getInviteId());

        InviteResponse second = createInvite(tenantId, "ttl@example.com", List.of("DPO"), 1440, "idem-ttl");
        assertThat(second.getInviteId()).isNotEqualTo(first.getInviteId());
        assertThat(second.getToken()).isNotEqualTo(first.getToken());
    }

    @Test
    void create_invite_idempotency_key_reused_different_body_conflict() {
        UUID tenantId = seedActiveTenant();
        seedRole(tenantId, "TENANT_ADMIN");
        seedRole(tenantId, "DPO");
        seedRole(tenantId, "CONNECTOR_AGENT");

        createInvite(tenantId, "user@example.com", List.of("DPO"), 1440, "idem-key-2");

        ResponseEntity<String> response = postInvite(tenantId, "user@example.com", List.of("CONNECTOR_AGENT"), 1440, "idem-key-2", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void active_invite_per_email_enforced() {
        UUID tenantId = seedActiveTenant();
        seedRole(tenantId, "TENANT_ADMIN");
        seedRole(tenantId, "DPO");

        createInvite(tenantId, "user@example.com", List.of("DPO"), 1440, null);
        ResponseEntity<String> response = postInvite(tenantId, "user@example.com", List.of("DPO"), 60, null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void accept_invite_happy_path_creates_user_and_consumes_invite() {
        UUID tenantId = seedActiveTenant();
        seedRole(tenantId, "TENANT_ADMIN");
        seedRole(tenantId, "DPO");

        InviteResponse invite = createInvite(tenantId, "accept@example.com", List.of("DPO"), 1440, null);

        AcceptInviteRequest accept = new AcceptInviteRequest();
        accept.setToken(invite.getToken());
        accept.setPassword("StrongPassw0rd!");

        ResponseEntity<AcceptInviteResponse> response = restTemplate.postForEntity("/users/invites/accept", accept, AcceptInviteResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTenantId()).isEqualTo(tenantId);

        UserInvite stored = userInviteRepository.findById(invite.getInviteId()).orElseThrow();
        assertThat(stored.getUsedAt()).isNotNull();
        assertThat(stored.getUsedByUserId()).isNotNull();
        assertThat(countAudit("INVITE_ACCEPTED", tenantId)).isEqualTo(1L);
        assertThat(countOutbox("INVITE_ACCEPTED", tenantId)).isEqualTo(1L);
    }

    @Test
    void accept_invite_single_use() {
        UUID tenantId = seedActiveTenant();
        seedRole(tenantId, "TENANT_ADMIN");
        seedRole(tenantId, "DPO");

        InviteResponse invite = createInvite(tenantId, "single@example.com", List.of("DPO"), 1440, null);

        AcceptInviteRequest accept = new AcceptInviteRequest();
        accept.setToken(invite.getToken());
        accept.setPassword("StrongPassw0rd!");

        ResponseEntity<AcceptInviteResponse> first = restTemplate.postForEntity("/users/invites/accept", accept, AcceptInviteResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> second = restTemplate.postForEntity("/users/invites/accept", accept, String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void accept_invite_expired() {
        UUID tenantId = seedActiveTenant();
        seedRole(tenantId, "TENANT_ADMIN");
        seedRole(tenantId, "DPO");

        InviteResponse invite = createInvite(tenantId, "expired@example.com", List.of("DPO"), 1440, null);
        jdbcTemplate.update("update identity.user_invites set expires_at = now() - interval '1 minute' where invite_id = ?", invite.getInviteId());

        AcceptInviteRequest accept = new AcceptInviteRequest();
        accept.setToken(invite.getToken());
        accept.setPassword("StrongPassw0rd!");

        ResponseEntity<String> response = restTemplate.postForEntity("/users/invites/accept", accept, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void accept_invite_invalid_cases_use_generic_error() throws Exception {
        UUID tenantId = seedActiveTenant();
        seedRole(tenantId, "TENANT_ADMIN");
        seedRole(tenantId, "DPO");

        InviteResponse invite = createInvite(tenantId, "enum@example.com", List.of("DPO"), 1440, null);

        AcceptInviteRequest expired = new AcceptInviteRequest();
        expired.setToken(invite.getToken());
        expired.setPassword("StrongPassw0rd!");
        jdbcTemplate.update("update identity.user_invites set expires_at = now() - interval '1 minute' where invite_id = ?", invite.getInviteId());

        ResponseEntity<String> expiredResponse = restTemplate.postForEntity("/users/invites/accept", expired, String.class);
        assertThat(expiredResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        AcceptInviteRequest invalid = new AcceptInviteRequest();
        invalid.setToken("not-a-real-token");
        invalid.setPassword("StrongPassw0rd!");
        ResponseEntity<String> invalidResponse = restTemplate.postForEntity("/users/invites/accept", invalid, String.class);
        assertThat(invalidResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        String expiredMessage = readErrorMessage(expiredResponse);
        String invalidMessage = readErrorMessage(invalidResponse);
        assertThat(expiredMessage).isEqualTo("INVITE_INVALID_OR_EXPIRED");
        assertThat(invalidMessage).isEqualTo("INVITE_INVALID_OR_EXPIRED");
    }

    @Test
    void evidence_failure_blocks_create_and_accept() {
        UUID tenantId = seedActiveTenant();
        seedRole(tenantId, "TENANT_ADMIN");
        seedRole(tenantId, "DPO");

        wireMockServer.stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse().withStatus(503)));

        ResponseEntity<String> createResponse = postInvite(tenantId, "fail@example.com", List.of("DPO"), 1440, null, String.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(userInviteRepository.findAll()).isEmpty();
        assertThat(countAudit("USER_INVITED", tenantId)).isEqualTo(0L);
        assertThat(countOutbox("USER_INVITED", tenantId)).isEqualTo(0L);

        wireMockServer.resetAll();
        wireMockServer.stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"evidenceId\":\"" + UUID.randomUUID() + "\",\"status\":\"CREATED\"}")));

        InviteResponse invite = createInvite(tenantId, "acceptfail@example.com", List.of("DPO"), 1440, null);

        wireMockServer.resetAll();
        wireMockServer.stubFor(post(urlEqualTo("/evidence"))
                .willReturn(aResponse().withStatus(503)));

        AcceptInviteRequest accept = new AcceptInviteRequest();
        accept.setToken(invite.getToken());
        accept.setPassword("StrongPassw0rd!");

        ResponseEntity<String> acceptResponse = restTemplate.postForEntity("/users/invites/accept", accept, String.class);
        assertThat(acceptResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(userRepository.existsByTenantIdAndEmail(tenantId, "acceptfail@example.com")).isFalse();
        assertThat(countAudit("INVITE_ACCEPTED", tenantId)).isEqualTo(0L);
        assertThat(countOutbox("INVITE_ACCEPTED", tenantId)).isEqualTo(0L);
    }

    @Test
    void tenant_status_gating() {
        UUID tenantId = seedActiveTenant();
        seedRole(tenantId, "TENANT_ADMIN");
        seedRole(tenantId, "DPO");

        jdbcTemplate.update("update identity.tenants set status = ? where tenant_id = ?", TenantStatuses.SUSPENDED, tenantId);

        ResponseEntity<String> response = postInvite(tenantId, "blocked@example.com", List.of("DPO"), 1440, null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        jdbcTemplate.update("update identity.tenants set status = ? where tenant_id = ?", TenantStatuses.ACTIVE, tenantId);
        InviteResponse invite = createInvite(tenantId, "blocked2@example.com", List.of("DPO"), 1440, null);

        jdbcTemplate.update("update identity.tenants set status = ? where tenant_id = ?", TenantStatuses.SUSPENDED, tenantId);

        AcceptInviteRequest accept = new AcceptInviteRequest();
        accept.setToken(invite.getToken());
        accept.setPassword("StrongPassw0rd!");

        ResponseEntity<String> acceptResponse = restTemplate.postForEntity("/users/invites/accept", accept, String.class);
        assertThat(acceptResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private InviteResponse createInvite(UUID tenantId, String email, List<String> roles, Integer expiresInMinutes, String idempotencyKey) {
        ResponseEntity<InviteResponse> response = postInvite(tenantId, email, roles, expiresInMinutes, idempotencyKey, InviteResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private <T> ResponseEntity<T> postInvite(UUID tenantId, String email, List<String> roles, Integer expiresInMinutes, String idempotencyKey, Class<T> responseType) {
        CreateInviteRequest request = new CreateInviteRequest();
        request.setEmail(email);
        request.setRoles(roles);
        request.setExpiresInMinutes(expiresInMinutes);

        HttpHeaders headers = headersForTenant(tenantId);
        if (idempotencyKey != null) {
            headers.set("X-Idempotency-Key", idempotencyKey);
        }

        return restTemplate.exchange(
                "/users/invites",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                responseType
        );
    }

    private HttpHeaders headersForTenant(UUID tenantId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", tenantId.toString());
        headers.set("X-Actor-Id", UUID.randomUUID().toString());
        return headers;
    }

    private UUID seedActiveTenant() {
        Tenant tenant = new Tenant();
        tenant.setName("Invite Tenant");
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

    private JsonNode readAuditMetadata(String action, UUID tenantId) throws Exception {
        String metadata = jdbcTemplate.queryForObject(
                "select metadata::text from identity.audit_events where tenant_id = ? and action = ? order by occurred_at desc limit 1",
                String.class,
                tenantId,
                action
        );
        return objectMapper.readTree(metadata);
    }

    private String readErrorMessage(ResponseEntity<String> response) throws Exception {
        JsonNode node = objectMapper.readTree(response.getBody());
        return node.get("message").asText();
    }
}
