package io.regulyn.identity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.model.ActorType;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import io.regulyn.identity.client.EvidenceClient;
import io.regulyn.identity.constants.TenantStatuses;
import io.regulyn.identity.dto.AcceptInviteRequest;
import io.regulyn.identity.dto.AcceptInviteResponse;
import io.regulyn.identity.dto.CreateInviteRequest;
import io.regulyn.identity.dto.InviteResponse;
import io.regulyn.identity.entity.IdempotencyKeyEntity;
import io.regulyn.identity.entity.IdempotencyKeyId;
import io.regulyn.identity.entity.Role;
import io.regulyn.identity.entity.Tenant;
import io.regulyn.identity.entity.User;
import io.regulyn.identity.entity.UserInvite;
import io.regulyn.identity.entity.UserRole;
import io.regulyn.identity.repository.IdempotencyKeyRepository;
import io.regulyn.identity.repository.RoleRepository;
import io.regulyn.identity.repository.TenantRepository;
import io.regulyn.identity.repository.UserInviteRepository;
import io.regulyn.identity.repository.UserRepository;
import io.regulyn.identity.repository.UserRoleRepository;
import io.regulyn.identity.util.CanonicalJson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserInviteService {

    private static final String SCOPE_CREATE_INVITE = "POST:/users/invites";
    private static final int DEFAULT_EXPIRES_MINUTES = 1440;
    private static final int MAX_EXPIRES_MINUTES = 10080;
    private static final int IDEMPOTENCY_TTL_DAYS = 7;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    private final TenantRepository tenantRepository;
    private final UserInviteRepository userInviteRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final InviteTokenService inviteTokenService;
    private final InviteTokenCrypto inviteTokenCrypto;
    private final PasswordEncoder passwordEncoder;
    private final EvidenceClient evidenceClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final String serviceName;
    private final TenantWriteGuard tenantWriteGuard;

    public UserInviteService(
            TenantRepository tenantRepository,
            UserInviteRepository userInviteRepository,
            RoleRepository roleRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            InviteTokenService inviteTokenService,
            InviteTokenCrypto inviteTokenCrypto,
            PasswordEncoder passwordEncoder,
            EvidenceClient evidenceClient,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper,
            TenantWriteGuard tenantWriteGuard,
            PlatformTransactionManager transactionManager,
            @Value("${spring.application.name:identity-tenant-service}") String serviceName) {
        this.tenantRepository = tenantRepository;
        this.userInviteRepository = userInviteRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.inviteTokenService = inviteTokenService;
        this.inviteTokenCrypto = inviteTokenCrypto;
        this.passwordEncoder = passwordEncoder;
        this.evidenceClient = evidenceClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.tenantWriteGuard = tenantWriteGuard;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.serviceName = serviceName;
    }

    public InviteResponse createInvite(CreateInviteRequest request, String idempotencyKey) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_CONTEXT_REQUIRED");
        }

        tenantWriteGuard.guardWrite(tenantId, "USER_INVITE_CREATE");

        String email = normalizeEmail(request.getEmail());
        List<String> roles = normalizeRoles(request.getRoles());
        int expiresInMinutes = resolveExpiryMinutes(request.getExpiresInMinutes());
        Instant expiresAt = Instant.now().plus(expiresInMinutes, ChronoUnit.MINUTES);

        ensureTenantActive(tenantId);
        List<Role> resolvedRoles = resolveRoles(tenantId, roles);

        String requestHash = computeRequestHash(tenantId, email, roles, expiresInMinutes);

        return transactionTemplate.execute(status -> {
            IdempotencyKeyEntity existingKey = null;
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                existingKey = idempotencyKeyRepository.findForUpdate(tenantId, SCOPE_CREATE_INVITE, idempotencyKey)
                        .orElse(null);
                if (existingKey != null && isIdempotencyExpired(existingKey)) {
                    idempotencyKeyRepository.delete(existingKey);
                    existingKey = null;
                }
                if (existingKey != null) {
                    if (!requestHash.equals(existingKey.getRequestHash())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED_DIFFERENT_REQUEST");
                    }
                    return responseFromIdempotency(existingKey);
                }
            }

            UserInvite existingInvite = userInviteRepository.findActiveByTenantIdAndEmailForUpdate(tenantId, email)
                    .orElse(null);
            if (existingInvite != null) {
                List<String> existingRoles = parseRoles(existingInvite.getRolesJson());
                boolean rolesMatch = normalizeRoles(existingRoles).equals(roles);
                long existingExpiryMinutes = existingInvite.getCreatedAt() != null
                        ? ChronoUnit.MINUTES.between(existingInvite.getCreatedAt(), existingInvite.getExpiresAt())
                        : -1;
                boolean expiryMatch = existingExpiryMinutes == expiresInMinutes;
                if (rolesMatch && expiryMatch) {
                    return buildInviteResponse(existingInvite, null);
                }
                throw new ResponseStatusException(HttpStatus.CONFLICT, "INVITE_ALREADY_EXISTS_DIFFERENT_PARAMETERS");
            }

            UUID evidenceId = createEvidence(
                    tenantId,
                    "USER_INVITED",
                    "USER_INVITED",
                    "User invited",
                    Map.of(
                            "tenantId", tenantId.toString(),
                            "email", email
                    )
            );

            String rawToken = inviteTokenService.generateToken();
            String tokenHash = inviteTokenService.hashToken(rawToken);

            UUID actorId = TenantContextHolder.getUserId();
            if (actorId != null && !userRepository.existsById(actorId)) {
                actorId = null;
            }

            UserInvite invite = new UserInvite();
            invite.setInviteId(UUID.randomUUID());
            invite.setTenantId(tenantId);
            invite.setEmail(email);
            invite.setRolesJson(writeRolesJson(roles));
            invite.setTokenHash(tokenHash);
            invite.setTokenHashAlg("HMAC_SHA256");
            invite.setExpiresAt(expiresAt);
            invite.setCreatedByUserId(actorId);

            userInviteRepository.save(invite);

            InviteResponse response = buildInviteResponse(invite, rawToken);
            writeAuditOutboxInvite("USER_INVITED", invite, response, evidenceId);

            if (existingKey == null && idempotencyKey != null && !idempotencyKey.isBlank()) {
                String aad = buildIdempotencyAad(tenantId, SCOPE_CREATE_INVITE, idempotencyKey);
                InviteTokenCrypto.EncryptedToken encrypted = inviteTokenCrypto.encrypt(rawToken, aad);
                String responseJson = serializeResponseWithEncryptedToken(response, encrypted);
                Instant idempotencyExpiresAt = resolveIdempotencyExpiry(invite.getExpiresAt());

                IdempotencyKeyEntity entity = new IdempotencyKeyEntity();
                entity.setId(new IdempotencyKeyId(tenantId, SCOPE_CREATE_INVITE, idempotencyKey));
                entity.setRequestHash(requestHash);
                entity.setResponseJson(responseJson);
                entity.setExpiresAt(idempotencyExpiresAt);
                idempotencyKeyRepository.save(entity);
            }

            return response;
        });
    }

    public AcceptInviteResponse acceptInvite(AcceptInviteRequest request) {
        if (request == null || request.getToken() == null || request.getToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVITE_TOKEN_REQUIRED");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PASSWORD_REQUIRED");
        }

        String tokenHash = inviteTokenService.hashToken(request.getToken());

        return transactionTemplate.execute(status -> {
            UserInvite invite = userInviteRepository.findByTokenHashForUpdate(tokenHash)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "INVITE_INVALID_OR_EXPIRED"));

            if (invite.getUsedAt() != null || invite.getExpiresAt().isBefore(Instant.now())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "INVITE_INVALID_OR_EXPIRED");
            }

            ensureTenantActive(invite.getTenantId());

            if (userRepository.existsByTenantIdAndEmail(invite.getTenantId(), invite.getEmail())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS");
            }

            UUID evidenceId = createEvidence(
                    invite.getTenantId(),
                    "INVITE_ACCEPTED",
                    "INVITE_ACCEPTED",
                    "Invite accepted",
                    Map.of(
                            "tenantId", invite.getTenantId().toString(),
                            "inviteId", invite.getInviteId().toString(),
                            "email", invite.getEmail()
                    )
            );

            User user = new User();
            user.setTenantId(invite.getTenantId());
            user.setEmail(invite.getEmail());
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            user.setEnabled(true);
            user.setCreatedBy(invite.getCreatedByUserId());
            user = userRepository.save(user);

            List<String> roleNames = parseRoles(invite.getRolesJson());
            List<Role> roles = resolveRoles(invite.getTenantId(), roleNames);
            for (Role role : roles) {
                UserRole assignment = new UserRole();
                assignment.setUserId(user.getUserId());
                assignment.setRoleId(role.getRoleId());
                assignment.setTenantId(invite.getTenantId());
                assignment.setAssignedBy(invite.getCreatedByUserId());
                userRoleRepository.save(assignment);
            }

            invite.setUsedAt(Instant.now());
            invite.setUsedByUserId(user.getUserId());
            userInviteRepository.save(invite);

            AcceptInviteResponse response = new AcceptInviteResponse();
            response.setUserId(user.getUserId());
            response.setTenantId(invite.getTenantId());
            response.setEmail(user.getEmail());
            response.setRoles(roleNames);

            writeAuditOutboxAccept(invite, user, roleNames, evidenceId);

            return response;
        });
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EMAIL_REQUIRED");
        }
        String trimmed = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
        }
        return trimmed;
    }

    private List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROLES_REQUIRED");
        }
        List<String> normalized = new ArrayList<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ROLE");
            }
            normalized.add(role.trim());
        }
        Collections.sort(normalized);
        return normalized;
    }

    private int resolveExpiryMinutes(Integer expiresInMinutes) {
        int value = expiresInMinutes != null ? expiresInMinutes : DEFAULT_EXPIRES_MINUTES;
        if (value <= 0 || value > MAX_EXPIRES_MINUTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_EXPIRY_MINUTES");
        }
        return value;
    }

    private void ensureTenantActive(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_NOT_FOUND"));
        if (!TenantStatuses.ACTIVE.equals(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_NOT_ACTIVE");
        }
    }

    private List<Role> resolveRoles(UUID tenantId, List<String> roles) {
        List<Role> resolved = new ArrayList<>();
        for (String roleName : roles) {
            Role role = roleRepository.findByTenantIdAndRoleName(tenantId, roleName)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROLE_NOT_FOUND"));
            resolved.add(role);
        }
        return resolved;
    }

    private String computeRequestHash(UUID tenantId, String email, List<String> roles, int expiresInMinutes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("email", email);
        payload.put("roles", roles);
        payload.put("expiresInMinutes", expiresInMinutes);
        JsonNode canonical = CanonicalJson.canonicalize(objectMapper, objectMapper.valueToTree(payload));
        String canonicalJson = CanonicalJson.writeCanonicalJson(objectMapper, canonical);
        return sha256(canonicalJson);
    }

    private InviteResponse responseFromIdempotency(IdempotencyKeyEntity entity) {
        try {
            JsonNode node = objectMapper.readTree(entity.getResponseJson());
            String tokenIv = node.path("tokenIv").asText(null);
            String tokenCiphertext = node.path("tokenCiphertext").asText(null);
            String token = null;
            if (tokenIv != null && tokenCiphertext != null) {
                String aad = buildIdempotencyAad(entity.getId().getTenantId(), entity.getId().getScope(), entity.getId().getIdempotencyKey());
                token = inviteTokenCrypto.decrypt(tokenIv, tokenCiphertext, aad);
            }
            InviteResponse response = new InviteResponse();
            response.setInviteId(UUID.fromString(node.get("inviteId").asText()));
            response.setTenantId(UUID.fromString(node.get("tenantId").asText()));
            response.setEmail(node.get("email").asText());
            List<String> roles = new ArrayList<>();
            node.get("roles").forEach(r -> roles.add(r.asText()));
            response.setRoles(roles);
            response.setExpiresAt(Instant.parse(node.get("expiresAt").asText()));
            response.setToken(token);
            return response;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCY_REPLAY_FAILED", ex);
        }
    }

    private String serializeResponseWithEncryptedToken(InviteResponse response, InviteTokenCrypto.EncryptedToken encrypted) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inviteId", response.getInviteId().toString());
        payload.put("tenantId", response.getTenantId().toString());
        payload.put("email", response.getEmail());
        payload.put("roles", response.getRoles());
        payload.put("expiresAt", response.getExpiresAt().toString());
        payload.put("tokenIv", encrypted.ivBase64());
        payload.put("tokenCiphertext", encrypted.ciphertextBase64());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize idempotency response", ex);
        }
    }

    private InviteResponse buildInviteResponse(UserInvite invite, String token) {
        InviteResponse response = new InviteResponse();
        response.setInviteId(invite.getInviteId());
        response.setTenantId(invite.getTenantId());
        response.setEmail(invite.getEmail());
        response.setRoles(parseRoles(invite.getRolesJson()));
        response.setExpiresAt(invite.getExpiresAt());
        response.setToken(token);
        return response;
    }

    private List<String> parseRoles(String rolesJson) {
        try {
            if (rolesJson == null) {
                return List.of();
            }
            JsonNode node = objectMapper.readTree(rolesJson);
            List<String> roles = new ArrayList<>();
            node.forEach(role -> roles.add(role.asText()));
            return roles;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse invite roles", ex);
        }
    }

    private String writeRolesJson(List<String> roles) {
        try {
            return objectMapper.writeValueAsString(roles);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize invite roles", ex);
        }
    }

    private UUID createEvidence(UUID tenantId, String eventType, String evidenceType, String description, Map<String, Object> metadata) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", Optional.ofNullable(TenantContextHolder.getUserId()).map(UUID::toString).orElse(null));
        payload.put("eventType", eventType);
        payload.put("evidenceType", evidenceType);
        payload.put("description", description);
        Map<String, Object> safeMetadata = new LinkedHashMap<>();
        safeMetadata.put("tenantId", tenantId.toString());
        safeMetadata.put("requestId", TenantContextHolder.getRequestId());
        safeMetadata.put("traceId", TenantContextHolder.getTraceId());
        if (metadata != null) {
            safeMetadata.putAll(metadata);
        }
        payload.put("metadata", safeMetadata);
        try {
            return evidenceClient.createEvidence(payload);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "EVIDENCE_UNAVAILABLE", ex);
        }
    }

    private void writeAuditOutboxInvite(String action, UserInvite invite, InviteResponse response, UUID evidenceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", invite.getTenantId().toString());
        payload.put("inviteId", invite.getInviteId().toString());
        payload.put("email", invite.getEmail());
        payload.put("roles", response.getRoles());
        payload.put("expiresAt", invite.getExpiresAt());
        payload.put("actorUserId", Optional.ofNullable(TenantContextHolder.getUserId()).map(UUID::toString).orElse(null));
        payload.put("actorRoles", TenantContextHolder.getRoles());
        payload.put("requestId", TenantContextHolder.getRequestId());
        payload.put("traceId", TenantContextHolder.getTraceId());
        writeAuditOutbox(action, invite.getTenantId(), payload, evidenceId);
    }

    private void writeAuditOutboxAccept(UserInvite invite, User user, List<String> roles, UUID evidenceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", invite.getTenantId().toString());
        payload.put("inviteId", invite.getInviteId().toString());
        payload.put("userId", user.getUserId().toString());
        payload.put("email", user.getEmail());
        payload.put("roles", roles);
        payload.put("requestId", TenantContextHolder.getRequestId());
        payload.put("traceId", TenantContextHolder.getTraceId());
        writeAuditOutbox("INVITE_ACCEPTED", invite.getTenantId(), payload, evidenceId);
    }

    private void writeAuditOutbox(String action, UUID tenantId, Map<String, Object> payload, UUID evidenceId) {
        if (evidenceId != null) {
            payload.put("evidenceId", evidenceId.toString());
        }
        JsonNode payloadNode = objectMapper.valueToTree(payload);
        JsonNode canonicalPayload = CanonicalJson.canonicalize(objectMapper, payloadNode);
        String canonicalJson = CanonicalJson.writeCanonicalJson(objectMapper, canonicalPayload);
        Instant occurredAt = Instant.now();
        String payloadHash = sha256(action + "|" + tenantId + "|" + occurredAt + "|" + canonicalJson);

        AuditEvent.ActorType actorType = TenantContextHolder.getUserId() != null
                ? AuditEvent.ActorType.USER
                : AuditEvent.ActorType.SYSTEM;

        AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(TenantContextHolder.getUserId())
                .actorType(actorType)
                .service(serviceName)
                .action(action)
                .entityType("INVITE")
                .entityId(payload.get("inviteId") != null ? payload.get("inviteId").toString() : null)
                .payloadHash(payloadHash)
                .evidenceId(evidenceId)
                .timestamp(occurredAt)
                .metadata(canonicalPayload)
                .build();

        auditWriter.write(auditEvent);

        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setEventType(action);
        envelope.setTenantId(tenantId);
        envelope.setActorId(TenantContextHolder.getUserId());
        envelope.setActorType(TenantContextHolder.getUserId() != null ? ActorType.USER : ActorType.SYSTEM);
        envelope.setSourceService(serviceName);
        envelope.setEntityType("INVITE");
        envelope.setEntityId(payload.get("inviteId") != null ? payload.get("inviteId").toString() : null);
        envelope.setOccurredAt(occurredAt);
        envelope.setCorrelationId(resolveCorrelationId());
        envelope.setPayload(canonicalPayload);
        envelope.setPayloadHash(payloadHash);
        envelope.setIdempotencyKey(null);

        outboxWriter.write(envelope);
    }

    private String resolveCorrelationId() {
        if (TenantContextHolder.getRequestId() != null) {
            return TenantContextHolder.getRequestId();
        }
        if (TenantContextHolder.getTraceId() != null) {
            return TenantContextHolder.getTraceId();
        }
        return UUID.randomUUID().toString();
    }

    private boolean isIdempotencyExpired(IdempotencyKeyEntity entity) {
        Instant expiresAt = entity.getExpiresAt();
        return expiresAt != null && !expiresAt.isAfter(Instant.now());
    }

    private Instant resolveIdempotencyExpiry(Instant inviteExpiresAt) {
        Instant ttlExpiry = Instant.now().plus(IDEMPOTENCY_TTL_DAYS, ChronoUnit.DAYS);
        if (inviteExpiresAt != null && inviteExpiresAt.isBefore(ttlExpiry)) {
            return inviteExpiresAt;
        }
        return ttlExpiry;
    }

    private String buildIdempotencyAad(UUID tenantId, String scope, String idempotencyKey) {
        return tenantId + "|" + scope + "|" + idempotencyKey;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
