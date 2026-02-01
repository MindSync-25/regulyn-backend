package com.regulyn.guardian.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.guardian.client.EvidenceServiceClient;
import com.regulyn.guardian.dto.*;
import com.regulyn.guardian.entity.*;
import com.regulyn.guardian.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
public class ConsentService {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsentService.class);
    private static final Set<String> PRIVILEGED_ROLES = Set.of("TENANT_ADMIN", "DPO", "REVIEWER");
    
    private final GuardianConsentRepository consentRepository;
    private final ConsentSignedArtifactRepository artifactRepository;
    private final GuardianRepository guardianRepository;
    private final ChildService childService;
    private final GuardianService guardianService;
    private final ConsentStateMachine stateMachine;
    private final ConsentStatusHistoryService historyService;
    private final EvidenceServiceClient evidenceClient;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    
    public ConsentService(
            GuardianConsentRepository consentRepository,
            ConsentSignedArtifactRepository artifactRepository,
            GuardianRepository guardianRepository,
            ChildService childService,
            GuardianService guardianService,
            ConsentStateMachine stateMachine,
            ConsentStatusHistoryService historyService,
            EvidenceServiceClient evidenceClient,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter) {
        this.consentRepository = consentRepository;
        this.artifactRepository = artifactRepository;
        this.guardianRepository = guardianRepository;
        this.childService = childService;
        this.guardianService = guardianService;
        this.stateMachine = stateMachine;
        this.historyService = historyService;
        this.evidenceClient = evidenceClient;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
    }
    
    @Transactional
    public CreateConsentResponse createConsent(CreateConsentRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();
        
        // Check idempotency
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<GuardianConsent> existing = consentRepository.findByIdempotencyKey(
                tenantId, request.childId(), request.guardianId(), 
                request.purposeKey(), request.idempotencyKey());
            if (existing.isPresent()) {
                logger.info("Returning existing consent for idempotencyKey: {}", request.idempotencyKey());
                return new CreateConsentResponse(existing.get().getConsentId(), existing.get().getStatus());
            }
        }
        
        // Verify child and guardian exist
        Child child = childService.getChild(request.childId());
        Guardian guardian = guardianService.getGuardian(request.guardianId());
        
        // Validate guardian eligibility
        if (!stateMachine.isGuardianEligible(guardian.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                "Guardian must be VERIFIED to create consent");
        }
        
        if (ConsentStateMachine.GUARDIAN_DISABLED.equals(guardian.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                "Guardian is DISABLED and cannot create consent");
        }
        
        // Validate date range
        if (request.validFrom() != null && request.validTo() != null) {
            if (request.validTo().isBefore(request.validFrom())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "validTo must be >= validFrom");
            }
        }
        
        GuardianConsent consent = new GuardianConsent();
        consent.setTenantId(tenantId);
        consent.setChildId(request.childId());
        consent.setGuardianId(request.guardianId());
        consent.setPurposeKey(request.purposeKey());
        consent.setConsentScope(request.consentScope());
        consent.setStatus(ConsentStateMachine.STATUS_SUBMITTED);
        consent.setRequiresApproval(request.requiresApproval() == null || request.requiresApproval());
        consent.setIdempotencyKey(request.idempotencyKey());
        consent.setValidFrom(request.validFrom());
        consent.setValidTo(request.validTo());
        
        consent = consentRepository.save(consent);
        
        // Save signed artifact
        if (request.signedArtifact() != null) {
            ConsentSignedArtifact artifact = new ConsentSignedArtifact();
            artifact.setConsentId(consent.getConsentId());
            artifact.setArtifactRef(request.signedArtifact().artifactRef());
            artifact.setArtifactType(request.signedArtifact().artifactType());
            artifact.setContentHash(request.signedArtifact().contentHash());
            artifact.setSignedAt(request.signedArtifact().signedAt());
            artifactRepository.save(artifact);
        }
        
        // Record status history
        historyService.recordStatusChange(
            consent.getConsentId(),
            null,
            ConsentStateMachine.STATUS_SUBMITTED,
            actorId,
            "Consent submitted"
        );
        
        writeAudit(actorId, "consent.submitted", "GuardianConsent", consent.getConsentId(), 
            Map.of("purposeKey", request.purposeKey(), "childId", request.childId().toString()));
        writeOutbox(tenantId, "consent.submitted", Map.of(
            "consentId", consent.getConsentId().toString(),
            "childId", request.childId().toString(),
            "guardianId", request.guardianId().toString(),
            "purposeKey", request.purposeKey(),
            "status", consent.getStatus()
        ));
        
        logger.info("Created consent: {} for child: {}", consent.getConsentId(), request.childId());
        
        return new CreateConsentResponse(consent.getConsentId(), consent.getStatus());
    }
    
    @Transactional
    public ApproveConsentResponse approveConsent(UUID consentId, ApproveConsentRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();
        Set<String> roles = context.getRoles();
        String actorRole = roles != null && !roles.isEmpty() ? roles.iterator().next() : null;
        
        // Role-based access control
        if (actorRole == null || !PRIVILEGED_ROLES.contains(actorRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "Only TENANT_ADMIN, DPO, or REVIEWER can approve consents");
        }
        
        GuardianConsent consent = getConsent(consentId);
        
        String currentStatus = consent.getStatus();
        String newStatus = "APPROVED".equals(request.decision()) ? 
            ConsentStateMachine.STATUS_APPROVED : ConsentStateMachine.STATUS_REJECTED;
        
        // Validate transition
        if (!stateMachine.canTransition(currentStatus, newStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                "Invalid transition from " + currentStatus + " to " + newStatus);
        }
        
        consent.setStatus(newStatus);
        
        if (ConsentStateMachine.STATUS_APPROVED.equals(newStatus)) {
            consent.setApprovedBy(actorId);
            consent.setApprovedAt(Instant.now());
        }
        
        consent = consentRepository.save(consent);
        
        // Record status history
        historyService.recordStatusChange(
            consentId,
            currentStatus,
            newStatus,
            actorId,
            request.notes()
        );
        
        String eventType = ConsentStateMachine.STATUS_APPROVED.equals(newStatus) ? 
            "consent.approved" : "consent.rejected";
        
        writeAudit(actorId, eventType, "GuardianConsent", consentId, 
            Map.of("decision", request.decision(), "approvedBy", actorId.toString()));
        writeOutbox(tenantId, eventType, Map.of(
            "consentId", consentId.toString(),
            "status", consent.getStatus(),
            "approvedBy", actorId.toString()
        ));
        
        logger.info("{} consent: {} by {}", newStatus, consentId, actorId);
        
        return new ApproveConsentResponse(consentId, consent.getStatus(), consent.getApprovedAt());
    }
    
    @Transactional
    public RevokeConsentResponse revokeConsent(UUID consentId, RevokeConsentRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();
        
        GuardianConsent consent = getConsent(consentId);
        
        // Validate can revoke
        if (!stateMachine.canRevoke(consent.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                "Can only revoke consents in APPROVED status, current status: " + consent.getStatus());
        }
        
        String currentStatus = consent.getStatus();
        consent.setStatus(ConsentStateMachine.STATUS_REVOKED);
        consent.setRevokedAt(Instant.now());
        consent.setRevokedBy(actorId);
        consent.setRevokeReason(request.reason());
        
        consent = consentRepository.save(consent);
        
        // Record status history
        historyService.recordStatusChange(
            consentId,
            currentStatus,
            ConsentStateMachine.STATUS_REVOKED,
            actorId,
            request.reason()
        );
        
        writeAudit(actorId, "consent.revoked", "GuardianConsent", consentId, 
            Map.of("revokedBy", actorId.toString()));
        writeOutbox(tenantId, "consent.revoked", Map.of(
            "consentId", consentId.toString(),
            "status", consent.getStatus(),
            "revokedAt", consent.getRevokedAt().toString()
        ));
        
        logger.info("Revoked consent: {} by {}", consentId, actorId);
        
        return new RevokeConsentResponse(consentId, consent.getStatus(), consent.getRevokedAt());
    }
    
    @Transactional
    public CloseConsentResponse closeConsent(UUID consentId, CloseConsentRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();
        
        GuardianConsent consent = getConsent(consentId);
        
        String currentStatus = consent.getStatus();
        
        // Validate can close
        if (!stateMachine.canTransition(currentStatus, ConsentStateMachine.STATUS_CLOSED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                "Cannot close consent in status: " + currentStatus);
        }
        
        // Create evidence bundle (requires evidence service)
        try {
            // Create evidence entry
            UUID evidenceId = evidenceClient.createEvidence(
                "GUARDIAN_CONSENT",
                Map.of(
                    "consentId", consentId.toString(),
                    "purposeKey", consent.getPurposeKey(),
                    "status", currentStatus
                ),
                "consent-closure-" + consentId
            );
            
            // Collect all evidence IDs
            List<UUID> allEvidenceIds = new ArrayList<>();
            allEvidenceIds.add(evidenceId);
            if (request.includeEvidenceIds() != null) {
                allEvidenceIds.addAll(request.includeEvidenceIds());
            }
            
            // Create bundle
            UUID bundleId = evidenceClient.createBundle(
                "GUARDIAN_CONSENT",
                consentId,
                allEvidenceIds
            );
            
            consent.setEvidenceBundleId(bundleId);
            
        } catch (ResponseStatusException e) {
            // If evidence service is down, return 503 without changing status
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, 
                "Evidence service unavailable, cannot close consent: " + e.getMessage(), e);
        }
        
        consent.setStatus(ConsentStateMachine.STATUS_CLOSED);
        consent.setClosedAt(Instant.now());
        consent.setClosureNotes(request.closureNotes());
        
        consent = consentRepository.save(consent);
        
        // Record status history
        historyService.recordStatusChange(
            consentId,
            currentStatus,
            ConsentStateMachine.STATUS_CLOSED,
            actorId,
            request.closureNotes()
        );
        
        writeAudit(actorId, "consent.closed", "GuardianConsent", consentId, 
            Map.of("bundleId", consent.getEvidenceBundleId().toString()));
        writeOutbox(tenantId, "consent.closed", Map.of(
            "consentId", consentId.toString(),
            "status", consent.getStatus(),
            "bundleId", consent.getEvidenceBundleId().toString()
        ));
        
        logger.info("Closed consent: {} with bundle {}", consentId, consent.getEvidenceBundleId());
        
        return new CloseConsentResponse(consentId, consent.getStatus(), consent.getEvidenceBundleId());
    }
    
    @Transactional(readOnly = true)
    public GuardianConsent getConsent(UUID consentId) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        return consentRepository.findByTenantIdAndConsentId(tenantId, consentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consent not found"));
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
                "GuardianConsent",
                safePayload.getOrDefault("consentId", "").toString(),
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
