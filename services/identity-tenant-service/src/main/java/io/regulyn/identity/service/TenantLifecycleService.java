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
import io.regulyn.identity.dto.*;
import io.regulyn.identity.entity.Role;
import io.regulyn.identity.entity.Tenant;
import io.regulyn.identity.entity.User;
import io.regulyn.identity.entity.UserRole;
import io.regulyn.identity.repository.RoleRepository;
import io.regulyn.identity.repository.TenantRepository;
import io.regulyn.identity.repository.UserRepository;
import io.regulyn.identity.repository.UserRoleRepository;
import io.regulyn.identity.util.CanonicalJson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@Service
public class TenantLifecycleService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EvidenceClient evidenceClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final String serviceName;
    private final TransactionTemplate transactionTemplate;

    public TenantLifecycleService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            EvidenceClient evidenceClient,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            @Value("${spring.application.name:identity-tenant-service}") String serviceName) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.evidenceClient = evidenceClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.serviceName = serviceName;
    }

    private <T> T inTransaction(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        Tenant tenant = new Tenant();
        tenant.setName(request.getName());
        tenant.setPlanCode(request.getPlanCode());
        tenant.setCreatedBy(TenantContextHolder.getUserId());
        tenant.setStatus(TenantStatuses.DRAFT);

        Tenant saved = tenantRepository.save(tenant);

        Map<String, Object> payload = basePayload(saved);
        addStatusTransition(payload, null, TenantStatuses.DRAFT);
        payload.put("createdAt", saved.getCreatedAt());
        writeAuditOutbox("TENANT_CREATED_DRAFT", saved.getTenantId(), payload, null);

        return toResponse(saved);
    }

    public BootstrapAdminResponse bootstrapAdmin(UUID tenantId, BootstrapAdminRequest request) {
        // Only check tenant context if one exists (skip during public signup)
        UUID contextTenant = TenantContextHolder.getTenantId();
        if (contextTenant != null) {
            ensureTenantContextMatches(tenantId);
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));

        if (tenant.getAdminBootstrappedAt() != null || tenant.getAdminBootstrapUserId() != null) {
            UUID adminId = tenant.getAdminBootstrapUserId();
            if (adminId == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_ADMIN_BOOTSTRAP_INCOMPLETE");
            }
            User admin = userRepository.findById(adminId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ADMIN_USER_NOT_FOUND"));
            return toBootstrapResponse(admin);
        }

        if (!TenantStatuses.DRAFT.equals(tenant.getStatus()) && !TenantStatuses.ACTIVE.equals(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_NOT_BOOTSTRAPPABLE");
        }

        UUID evidenceId = null;
        try {
            evidenceId = createEvidence(
                    tenantId,
                    "TENANT_ADMIN_BOOTSTRAPPED",
                    "TENANT_ADMIN_BOOTSTRAP",
                    "Tenant admin bootstrapped",
                    Map.of(
                            "tenantId", tenantId.toString(),
                            "email", request.getEmail()
                    )
            );
        } catch (Exception ex) {
            // Evidence service unavailable - continue without evidence (development/signup scenario)
            System.err.println("WARNING: Evidence creation failed during bootstrap: " + ex.getMessage());
        }

        final UUID finalEvidenceId = evidenceId;
        return inTransaction(() -> transactionalBootstrap(tenantId, request, finalEvidenceId));
    }

    protected BootstrapAdminResponse transactionalBootstrap(UUID tenantId, BootstrapAdminRequest request, UUID evidenceId) {
        Tenant tenant = tenantRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));

        String previousStatus = tenant.getStatus();

        if (tenant.getAdminBootstrappedAt() != null || tenant.getAdminBootstrapUserId() != null) {
            UUID adminId = tenant.getAdminBootstrapUserId();
            if (adminId == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_ADMIN_BOOTSTRAP_INCOMPLETE");
            }
            User admin = userRepository.findById(adminId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ADMIN_USER_NOT_FOUND"));
            return toBootstrapResponse(admin);
        }

        // Create default roles if they don't exist
        Role adminRole = roleRepository.findByTenantIdAndRoleName(tenant.getTenantId(), "TENANT_ADMIN")
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setTenantId(tenant.getTenantId());
                    role.setRoleName("TENANT_ADMIN");
                    role.setDescription("Full administrative access to tenant");
                    role.setCreatedBy(TenantContextHolder.getUserId());
                    return roleRepository.save(role);
                });

        User user = new User();
        user.setTenantId(tenant.getTenantId());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setCreatedBy(TenantContextHolder.getUserId());
        user = userRepository.save(user);

        UserRole assignment = new UserRole();
        assignment.setUserId(user.getUserId());
        assignment.setRoleId(adminRole.getRoleId());
        assignment.setTenantId(tenant.getTenantId());
        assignment.setAssignedBy(TenantContextHolder.getUserId());
        userRoleRepository.save(assignment);

        tenant.setAdminBootstrappedAt(Instant.now());
        tenant.setAdminBootstrapUserId(user.getUserId());
        tenantRepository.save(tenant);

        Map<String, Object> payload = basePayload(tenant);
        payload.put("adminUserId", user.getUserId().toString());
        payload.put("adminEmail", user.getEmail());

        writeAuditOutbox("TENANT_ADMIN_BOOTSTRAPPED", tenant.getTenantId(), payload, evidenceId);

        return toBootstrapResponse(user);
    }

    public TenantResponse activate(UUID tenantId) {
        return activate(tenantId, false);
    }

    public TenantResponse activate(UUID tenantId, boolean skipContextCheck) {
        if (!skipContextCheck) {
            ensureTenantContextMatches(tenantId);
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));

        if (TenantStatuses.ACTIVE.equals(tenant.getStatus())) {
            return toResponse(tenant);
        }

        if (!TenantLifecyclePolicy.canActivate(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_INVALID_TRANSITION");
        }

        UUID evidenceId = createEvidence(
                tenantId,
                "TENANT_ACTIVATED",
                "TENANT_ACTIVATED",
                "Tenant activated",
                Map.of("tenantId", tenantId.toString())
        );

        return inTransaction(() -> transactionalActivate(tenantId, evidenceId));
    }

    protected TenantResponse transactionalActivate(UUID tenantId, UUID evidenceId) {
        Tenant tenant = tenantRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));

        String previousStatus = tenant.getStatus();

        if (TenantStatuses.ACTIVE.equals(tenant.getStatus())) {
            return toResponse(tenant);
        }

        if (!TenantLifecyclePolicy.canActivate(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_INVALID_TRANSITION");
        }

        tenant.setStatus(TenantStatuses.ACTIVE);
        tenant.setActivatedAt(Instant.now());
        tenantRepository.save(tenant);

        Map<String, Object> payload = basePayload(tenant);
        addStatusTransition(payload, previousStatus, TenantStatuses.ACTIVE);
        payload.put("activatedAt", tenant.getActivatedAt());
        writeAuditOutbox("TENANT_ACTIVATED", tenant.getTenantId(), payload, evidenceId);

        return toResponse(tenant);
    }

    public TenantResponse suspend(UUID tenantId, SuspendTenantRequest request) {
        return suspend(tenantId, request, false);
    }

    public TenantResponse suspend(UUID tenantId, SuspendTenantRequest request, boolean skipContextCheck) {
        if (!skipContextCheck) {
            ensureTenantContextMatches(tenantId);
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));

        if (TenantStatuses.SUSPENDED.equals(tenant.getStatus())) {
            return toResponse(tenant);
        }

        if (!TenantLifecyclePolicy.canSuspend(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_INVALID_TRANSITION");
        }

        Map<String, Object> suspendMetadata = new LinkedHashMap<>();
        suspendMetadata.put("tenantId", tenantId.toString());
        if (request != null && request.getReason() != null) {
            suspendMetadata.put("reason", request.getReason());
        }

        UUID evidenceId = createEvidence(
            tenantId,
            "TENANT_SUSPENDED",
            "TENANT_SUSPENDED",
            "Tenant suspended",
            suspendMetadata
        );

        return inTransaction(() -> transactionalSuspend(tenantId, request, evidenceId));
    }

    protected TenantResponse transactionalSuspend(UUID tenantId, SuspendTenantRequest request, UUID evidenceId) {
        Tenant tenant = tenantRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));

        String previousStatus = tenant.getStatus();

        if (TenantStatuses.SUSPENDED.equals(tenant.getStatus())) {
            return toResponse(tenant);
        }

        if (!TenantLifecyclePolicy.canSuspend(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_INVALID_TRANSITION");
        }

        tenant.setStatus(TenantStatuses.SUSPENDED);
        tenant.setSuspendedAt(Instant.now());
        tenantRepository.save(tenant);

        Map<String, Object> payload = basePayload(tenant);
        addStatusTransition(payload, previousStatus, TenantStatuses.SUSPENDED);
        payload.put("suspendedAt", tenant.getSuspendedAt());
        if (request != null && request.getReason() != null) {
            payload.put("reason", request.getReason());
        }

        writeAuditOutbox("TENANT_SUSPENDED", tenant.getTenantId(), payload, evidenceId);

        return toResponse(tenant);
    }

    public TenantResponse resume(UUID tenantId) {
        return resume(tenantId, false);
    }

    public TenantResponse resume(UUID tenantId, boolean skipContextCheck) {
        if (!skipContextCheck) {
            ensureTenantContextMatches(tenantId);
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));

        if (TenantStatuses.ACTIVE.equals(tenant.getStatus())) {
            return toResponse(tenant);
        }

        if (!TenantLifecyclePolicy.canResume(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_INVALID_TRANSITION");
        }

        UUID evidenceId = createEvidence(
                tenantId,
                "TENANT_RESUMED",
                "TENANT_RESUMED",
                "Tenant resumed",
                Map.of("tenantId", tenantId.toString())
        );

        return inTransaction(() -> transactionalResume(tenantId, evidenceId));
    }

    protected TenantResponse transactionalResume(UUID tenantId, UUID evidenceId) {
        Tenant tenant = tenantRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));

        String previousStatus = tenant.getStatus();

        if (TenantStatuses.ACTIVE.equals(tenant.getStatus())) {
            return toResponse(tenant);
        }

        if (!TenantLifecyclePolicy.canResume(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_INVALID_TRANSITION");
        }

        tenant.setStatus(TenantStatuses.ACTIVE);
        tenantRepository.save(tenant);

        Map<String, Object> payload = basePayload(tenant);
        addStatusTransition(payload, previousStatus, TenantStatuses.ACTIVE);
        writeAuditOutbox("TENANT_RESUMED", tenant.getTenantId(), payload, evidenceId);

        return toResponse(tenant);
    }

    public TenantResponse requestDelete(UUID tenantId, DeleteTenantRequest request) {
        ensureTenantContextMatches(tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));

        if (tenant.getDeleteRequestedAt() != null) {
            return toResponse(tenant);
        }

        if (!TenantLifecyclePolicy.canDeleteRequest(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_INVALID_TRANSITION");
        }

        Map<String, Object> deleteRequestMetadata = new LinkedHashMap<>();
        deleteRequestMetadata.put("tenantId", tenantId.toString());
        if (request != null && request.getReason() != null) {
            deleteRequestMetadata.put("reason", request.getReason());
        }

        UUID evidenceId = createEvidence(
            tenantId,
            "TENANT_DELETE_REQUESTED",
            "TENANT_DELETE_REQUESTED",
            "Tenant delete requested",
            deleteRequestMetadata
        );

        return inTransaction(() -> transactionalRequestDelete(tenantId, request, evidenceId));
    }

    protected TenantResponse transactionalRequestDelete(UUID tenantId, DeleteTenantRequest request, UUID evidenceId) {
        Tenant tenant = tenantRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));

        if (tenant.getDeleteRequestedAt() != null) {
            return toResponse(tenant);
        }

        if (!TenantLifecyclePolicy.canDeleteRequest(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_INVALID_TRANSITION");
        }

        tenant.setDeleteRequestedAt(Instant.now());
        tenantRepository.save(tenant);

        Map<String, Object> payload = basePayload(tenant);
        addStatusTransition(payload, tenant.getStatus(), tenant.getStatus());
        payload.put("deleteRequestedAt", tenant.getDeleteRequestedAt());
        if (request != null && request.getReason() != null) {
            payload.put("reason", request.getReason());
        }

        writeAuditOutbox("TENANT_DELETE_REQUESTED", tenant.getTenantId(), payload, evidenceId);

        return toResponse(tenant);
    }

    public TenantResponse delete(UUID tenantId, DeleteTenantRequest request) {
        ensureTenantContextMatches(tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));

        if (tenant.getDeleteRequestedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_DELETE_NOT_REQUESTED");
        }

        if (!TenantLifecyclePolicy.canHardDelete(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_ALREADY_DELETED");
        }

        if (Boolean.TRUE.equals(tenant.getComplianceHold())) {
            if (!auditExists("TENANT_DELETE_BLOCKED", tenant.getTenantId())) {
                Map<String, Object> deleteBlockedMetadata = new LinkedHashMap<>();
                deleteBlockedMetadata.put("tenantId", tenantId.toString());
                if (request != null && request.getReason() != null) {
                    deleteBlockedMetadata.put("reason", request.getReason());
                }

                UUID evidenceId = createEvidence(
                    tenantId,
                    "TENANT_DELETE_BLOCKED",
                    "TENANT_DELETE_BLOCKED",
                    "Tenant delete blocked",
                    deleteBlockedMetadata
                );
                inTransaction(() -> {
                    transactionalDeleteBlocked(tenantId, request, evidenceId);
                    return null;
                });
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_DELETE_BLOCKED");
        }

        Map<String, Object> deleteMetadata = new LinkedHashMap<>();
        deleteMetadata.put("tenantId", tenantId.toString());
        if (request != null && request.getReason() != null) {
            deleteMetadata.put("reason", request.getReason());
        }

        UUID evidenceId = createEvidence(
            tenantId,
            "TENANT_DELETED",
            "TENANT_DELETED",
            "Tenant deleted",
            deleteMetadata
        );

        return inTransaction(() -> transactionalDelete(tenantId, request, evidenceId));
    }

    protected void transactionalDeleteBlocked(UUID tenantId, DeleteTenantRequest request, UUID evidenceId) {
        Tenant tenant = tenantRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));

        Map<String, Object> payload = basePayload(tenant);
        addStatusTransition(payload, tenant.getStatus(), tenant.getStatus());
        if (request != null && request.getReason() != null) {
            payload.put("reason", request.getReason());
        }
        writeAuditOutbox("TENANT_DELETE_BLOCKED", tenant.getTenantId(), payload, evidenceId);
    }

    protected TenantResponse transactionalDelete(UUID tenantId, DeleteTenantRequest request, UUID evidenceId) {
        Tenant tenant = tenantRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));

        String previousStatus = tenant.getStatus();

        if (TenantStatuses.DELETED.equals(tenant.getStatus())) {
            return toResponse(tenant);
        }

        if (!TenantLifecyclePolicy.canHardDelete(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_ALREADY_DELETED");
        }

        if (tenant.getDeleteRequestedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_DELETE_NOT_REQUESTED");
        }

        tenant.setStatus(TenantStatuses.DELETED);
        tenant.setDeletedAt(Instant.now());
        tenantRepository.save(tenant);

        Map<String, Object> payload = basePayload(tenant);
        addStatusTransition(payload, previousStatus, TenantStatuses.DELETED);
        payload.put("deletedAt", tenant.getDeletedAt());
        if (request != null && request.getReason() != null) {
            payload.put("reason", request.getReason());
        }
        writeAuditOutbox("TENANT_DELETED", tenant.getTenantId(), payload, evidenceId);

        return toResponse(tenant);
    }

    private void ensureTenantContextMatches(UUID tenantId) {
        UUID contextTenant = TenantContextHolder.getTenantId();
        if (contextTenant != null && !contextTenant.equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_MISMATCH");
        }
    }

    private BootstrapAdminResponse toBootstrapResponse(User user) {
        BootstrapAdminResponse response = new BootstrapAdminResponse();
        response.setUserId(user.getUserId());
        response.setTenantId(user.getTenantId());
        response.setEmail(user.getEmail());
        return response;
    }

    private TenantResponse toResponse(Tenant tenant) {
        TenantResponse response = new TenantResponse();
        response.setTenantId(tenant.getTenantId());
        response.setName(tenant.getName());
        response.setStatus(tenant.getStatus());
        response.setCreatedAt(tenant.getCreatedAt());
        response.setUpdatedAt(tenant.getUpdatedAt());
        response.setActivatedAt(tenant.getActivatedAt());
        response.setSuspendedAt(tenant.getSuspendedAt());
        response.setDeletedAt(tenant.getDeletedAt());
        response.setAdminBootstrappedAt(tenant.getAdminBootstrappedAt());
        response.setAdminBootstrapUserId(tenant.getAdminBootstrapUserId());
        response.setDeleteRequestedAt(tenant.getDeleteRequestedAt());
        response.setComplianceHold(tenant.getComplianceHold());
        response.setReadOnly(tenant.getReadOnly());
        response.setPlanCode(tenant.getPlanCode());
        return response;
    }

    private Map<String, Object> basePayload(Tenant tenant) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenant.getTenantId().toString());
        payload.put("status", tenant.getStatus());
        payload.put("name", tenant.getName());
        payload.put("planCode", tenant.getPlanCode());
        payload.put("actorUserId", Optional.ofNullable(TenantContextHolder.getUserId()).map(UUID::toString).orElse(null));
        payload.put("actorRoles", TenantContextHolder.getRoles());
        payload.put("requestId", TenantContextHolder.getRequestId());
        payload.put("traceId", TenantContextHolder.getTraceId());
        return payload;
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
        safeMetadata.put("idempotencyKey", tenantId + ":" + eventType + ":" + TenantContextHolder.getRequestId());
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

    private void writeAuditOutbox(String action, UUID tenantId, Map<String, Object> payload, UUID evidenceId) {
        if (evidenceId != null) {
            payload.put("evidenceId", evidenceId.toString());
        }
        JsonNode payloadNode = objectMapper.valueToTree(payload);
        JsonNode canonicalPayloadNode = CanonicalJson.canonicalize(objectMapper, payloadNode);
        String canonicalPayloadJson = CanonicalJson.writeCanonicalJson(objectMapper, canonicalPayloadNode);
        Instant occurredAt = Instant.now();
        String payloadHash = sha256(action + "|" + tenantId + "|" + occurredAt + "|" + canonicalPayloadJson);

        AuditEvent.ActorType actorType = TenantContextHolder.getUserId() != null
                ? AuditEvent.ActorType.USER
                : AuditEvent.ActorType.SYSTEM;

        AuditEvent auditEvent = AuditEvent.builder()
                .tenantId(tenantId)
                .actorId(TenantContextHolder.getUserId())
                .actorType(actorType)
                .service(serviceName)
                .action(action)
                .entityType("TENANT")
                .entityId(tenantId.toString())
                .payloadHash(payloadHash)
                .evidenceId(evidenceId)
                .timestamp(occurredAt)
                .metadata(canonicalPayloadNode)
                .build();

        auditWriter.write(auditEvent);

        EventEnvelopeV1 envelope = new EventEnvelopeV1();
        envelope.setEventId(UUID.randomUUID());
        envelope.setEventType(action);
        envelope.setTenantId(tenantId);
        envelope.setActorId(TenantContextHolder.getUserId());
        envelope.setActorType(TenantContextHolder.getUserId() != null ? ActorType.USER : ActorType.SYSTEM);
        envelope.setSourceService(serviceName);
        envelope.setEntityType("TENANT");
        envelope.setEntityId(tenantId.toString());
        envelope.setOccurredAt(occurredAt);
        envelope.setCorrelationId(resolveCorrelationId());
        envelope.setPayload(canonicalPayloadNode);
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

    private boolean auditExists(String action, UUID tenantId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from identity.audit_events where tenant_id = ? and action = ?",
                Long.class,
                tenantId,
                action
        );
        return count != null && count > 0;
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

    private void addStatusTransition(Map<String, Object> payload, String previousStatus, String newStatus) {
        payload.put("previousStatus", previousStatus);
        payload.put("newStatus", newStatus);
    }

}
