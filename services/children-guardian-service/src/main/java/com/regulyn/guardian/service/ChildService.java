package com.regulyn.guardian.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.guardian.dto.CreateChildRequest;
import com.regulyn.guardian.dto.CreateChildResponse;
import com.regulyn.guardian.entity.Child;
import com.regulyn.guardian.repository.ChildRepository;
import com.regulyn.guardian.service.AgeRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ChildService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChildService.class);
    private static final int AGE_GATING_THRESHOLD = 18;
    
    private final ChildRepository childRepository;
    private final AgeRuleService ageRuleService;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    
    public ChildService(
            ChildRepository childRepository,
            AgeRuleService ageRuleService,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper) {
        this.childRepository = childRepository;
        this.ageRuleService = ageRuleService;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }
    
    @Transactional
    public CreateChildResponse createChild(CreateChildRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();
        
        // Check for duplicate childRef
        if (childRepository.existsByTenantIdAndChildRef(tenantId, request.childRef())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Child with this reference already exists");
        }
        
        Child child = new Child();
        child.setTenantId(tenantId);
        child.setChildRef(request.childRef());
        child.setFullName(request.fullName());
        child.setDateOfBirth(request.dateOfBirth());
        child.setCountry(request.country());
        String regionCountryCode = normalizeRegionCountry(request.regionCountryCode(), request.country());
        child.setRegionCountryCode(regionCountryCode);
        child.setRegionStateCode(normalizeRegionState(request.regionStateCode()));
        child.setStatus(request.status() != null ? request.status() : "ACTIVE");
        
        // Set metadata
        if (request.metadata() != null && !request.metadata().isEmpty()) {
            child.setMetadata(request.metadata());
        }
        
        child = childRepository.save(child);
        
        // Calculate age and age-gating
        int ageYears = calculateAge(child.getDateOfBirth());
        boolean isMinor = ageYears < AGE_GATING_THRESHOLD;
        if (regionCountryCode != null) {
            AgeRuleService.AgeEvaluationResult evaluation = ageRuleService.evaluate(
                tenantId,
                child.getChildId(),
                child.getDateOfBirth(),
                regionCountryCode,
                child.getRegionStateCode(),
                LocalDate.now()
            );
            ageYears = evaluation.ageYears();
            isMinor = evaluation.isMinor();
        }
        boolean requiresGuardianConsent = isMinor;
        
        writeAudit(actorId, "child.created", "Child", child.getChildId(), 
            Map.of("childRef", child.getChildRef(), "isMinor", isMinor));
        writeOutbox(tenantId, "child.created", Map.of(
            "childId", child.getChildId().toString(),
            "childRef", child.getChildRef(),
            "isMinor", isMinor,
            "requiresGuardianConsent", requiresGuardianConsent
        ));
        
        logger.info("Created child: {} (age: {}, isMinor: {})", child.getChildId(), ageYears, isMinor);
        
        return new CreateChildResponse(child.getChildId(), ageYears, isMinor, requiresGuardianConsent);
    }
    
    @Transactional(readOnly = true)
    public Child getChild(UUID childId) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        return childRepository.findByTenantIdAndChildId(tenantId, childId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Child not found"));
    }
    
    private int calculateAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return 0;
        }
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    private String normalizeRegionCountry(String regionCountryCode, String fallbackCountry) {
        String value = regionCountryCode != null && !regionCountryCode.isBlank()
                ? regionCountryCode
                : fallbackCountry;
        if (value == null) {
            return null;
        }
        return value.trim().toUpperCase();
    }

    private String normalizeRegionState(String regionStateCode) {
        if (regionStateCode == null || regionStateCode.isBlank()) {
            return null;
        }
        return regionStateCode.trim().toUpperCase();
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
                "Child",
                safePayload.getOrDefault("childId", "").toString(),
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
