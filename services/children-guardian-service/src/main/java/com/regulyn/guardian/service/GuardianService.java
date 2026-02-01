package com.regulyn.guardian.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.guardian.dto.CreateGuardianRequest;
import com.regulyn.guardian.dto.CreateGuardianResponse;
import com.regulyn.guardian.dto.VerifyGuardianRequest;
import com.regulyn.guardian.dto.VerifyGuardianResponse;
import com.regulyn.guardian.entity.Child;
import com.regulyn.guardian.entity.Guardian;
import com.regulyn.guardian.repository.GuardianRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class GuardianService {
    
    private static final Logger logger = LoggerFactory.getLogger(GuardianService.class);
    private static final Set<String> PRIVILEGED_ROLES = Set.of("TENANT_ADMIN", "DPO", "REVIEWER");
    
    private final GuardianRepository guardianRepository;
    private final ChildService childService;
    private final ConsentStateMachine stateMachine;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    
    public GuardianService(
            GuardianRepository guardianRepository,
            ChildService childService,
            ConsentStateMachine stateMachine,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter) {
        this.guardianRepository = guardianRepository;
        this.childService = childService;
        this.stateMachine = stateMachine;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
    }
    
    @Transactional
    public CreateGuardianResponse createGuardian(CreateGuardianRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();
        
        // Verify child exists
        Child child = childService.getChild(request.childId());
        
        // Check for duplicate guardian email for this child
        if (guardianRepository.findByTenantIdAndChildIdAndGuardianEmail(
                tenantId, request.childId(), request.guardianEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                "Guardian with this email already exists for this child");
        }
        
        Guardian guardian = new Guardian();
        guardian.setTenantId(tenantId);
        guardian.setChildId(request.childId());
        guardian.setGuardianName(request.guardianName());
        guardian.setGuardianEmail(request.guardianEmail());
        guardian.setGuardianPhone(request.guardianPhone());
        guardian.setRelationship(request.relationship());
        guardian.setVerificationRequired(request.verificationRequired() == null || request.verificationRequired());
        guardian.setStatus(ConsentStateMachine.GUARDIAN_PENDING);
        
        guardian = guardianRepository.save(guardian);
        
        writeAudit(actorId, "guardian.created", "Guardian", guardian.getGuardianId(), 
            Map.of("childId", request.childId().toString(), "email", request.guardianEmail()));
        writeOutbox(tenantId, "guardian.created", Map.of(
            "guardianId", guardian.getGuardianId().toString(),
            "childId", request.childId().toString(),
            "status", guardian.getStatus()
        ));
        
        logger.info("Created guardian: {} for child: {}", guardian.getGuardianId(), request.childId());
        
        return new CreateGuardianResponse(guardian.getGuardianId(), guardian.getStatus());
    }
    
    @Transactional
    public VerifyGuardianResponse verifyGuardian(UUID guardianId, VerifyGuardianRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();
        Set<String> roles = context.getRoles();
        String actorRole = roles != null && !roles.isEmpty() ? roles.iterator().next() : null;
        
        // Role-based access control
        if (actorRole == null || !PRIVILEGED_ROLES.contains(actorRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "Only TENANT_ADMIN, DPO, or REVIEWER can verify guardians");
        }
        
        Guardian guardian = getGuardian(guardianId);
        
        if (!ConsentStateMachine.GUARDIAN_PENDING.equals(guardian.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                "Guardian is not in PENDING status");
        }
        
        guardian.setStatus(ConsentStateMachine.GUARDIAN_VERIFIED);
        guardian.setVerifiedAt(Instant.now());
        guardian.setVerifiedBy(actorId);
        
        guardian = guardianRepository.save(guardian);
        
        writeAudit(actorId, "guardian.verified", "Guardian", guardianId, 
            Map.of("method", request.method() != null ? request.method() : "MANUAL", 
                   "verifiedBy", actorId.toString()));
        writeOutbox(tenantId, "guardian.verified", Map.of(
            "guardianId", guardianId.toString(),
            "status", guardian.getStatus(),
            "verifiedAt", guardian.getVerifiedAt().toString()
        ));
        
        logger.info("Verified guardian: {} by {}", guardianId, actorId);
        
        return new VerifyGuardianResponse(guardianId, guardian.getStatus(), guardian.getVerifiedAt());
    }
    
    @Transactional(readOnly = true)
    public Guardian getGuardian(UUID guardianId) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        return guardianRepository.findByTenantIdAndGuardianId(tenantId, guardianId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Guardian not found"));
    }
    
    private void writeAudit(UUID actorId, String action, String entityType, UUID entityId, Map<String, ?> details) {
        try {
            String hash = hashPayload(details.toString());
            TenantContext context = TenantContextHolder.getContext();
            
            AuditEvent event = AuditEvent.builder()
                .tenantId(context.getTenantId())
                .actorId(actorId)
                .actorType(AuditEvent.ActorType.USER)
                .service("children-guardian-service")
                .action(action)
                .entityType(entityType)
                .entityId(entityId.toString())
                .payloadHash(hash)
                .build();
            
            auditWriter.write(event);
        } catch (Exception e) {
            logger.error("Failed to write audit", e);
        }
    }
    
    private void writeOutbox(UUID tenantId, String eventType, Map<String, ?> payload) {
        try {
            Map<String, Object> safePayload = new HashMap<>();
            payload.forEach((k, v) -> safePayload.put(k, v != null ? v.toString() : ""));
            
            EventEnvelopeV1 event = EventFactory.create(
                eventType,
                "children-guardian-service",
                "Guardian",
                safePayload.getOrDefault("guardianId", "").toString(),
                safePayload
            );
            
            outboxWriter.write(event);
        } catch (Exception e) {
            logger.error("Failed to write outbox event", e);
        }
    }
    
    private String hashPayload(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
