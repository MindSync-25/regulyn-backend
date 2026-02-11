package com.regulyn.guardian.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditEvent;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.guardian.dto.CreateEsignRequestRequest;
import com.regulyn.guardian.dto.CreateEsignRequestResponse;
import com.regulyn.guardian.entity.EsignRequestEntity;
import com.regulyn.guardian.entity.Guardian;
import com.regulyn.guardian.entity.GuardianConsent;
import com.regulyn.guardian.esign.EsignCreateRequest;
import com.regulyn.guardian.esign.EsignCreateRequestResult;
import com.regulyn.guardian.esign.EsignProvider;
import com.regulyn.guardian.esign.EsignProviderRegistry;
import com.regulyn.guardian.esign.ProviderId;
import com.regulyn.guardian.repository.EsignRequestRepository;
import com.regulyn.guardian.repository.GuardianConsentRepository;
import com.regulyn.guardian.repository.GuardianRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class EsignRequestService {

    private static final Logger logger = LoggerFactory.getLogger(EsignRequestService.class);
    private static final Set<String> ELIGIBLE_GUARDIAN_STATUSES = Set.of(
            ConsentStateMachine.GUARDIAN_VERIFIED
    );

    private final EsignRequestRepository esignRequestRepository;
    private final GuardianConsentRepository consentRepository;
    private final GuardianRepository guardianRepository;
    private final EsignProviderRegistry providerRegistry;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;

    public EsignRequestService(
            EsignRequestRepository esignRequestRepository,
            GuardianConsentRepository consentRepository,
            GuardianRepository guardianRepository,
            EsignProviderRegistry providerRegistry,
            AuditWriter auditWriter,
            OutboxWriter outboxWriter) {
        this.esignRequestRepository = esignRequestRepository;
        this.consentRepository = consentRepository;
        this.guardianRepository = guardianRepository;
        this.providerRegistry = providerRegistry;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public CreateEsignRequestResponse createEsignRequest(UUID consentId, CreateEsignRequestRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Idempotency-Key is required");
        }

        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        UUID actorId = context.getUserId();

        GuardianConsent consent = consentRepository.findByTenantIdAndConsentId(tenantId, consentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consent not found"));

        Guardian guardian = guardianRepository.findByTenantIdAndGuardianId(tenantId, consent.getGuardianId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Guardian not found"));

        if (!ELIGIBLE_GUARDIAN_STATUSES.contains(guardian.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Guardian must be VERIFIED to request eSign");
        }

        Optional<EsignRequestEntity> existing = esignRequestRepository
                .findByTenantIdAndChildIdAndGuardianIdAndDocTypeAndDocVersion(
                        tenantId,
                        consent.getChildId(),
                        consent.getGuardianId(),
                        request.docType(),
                        request.docVersion());

        if (existing.isPresent()) {
            EsignRequestEntity entity = existing.get();
            return new CreateEsignRequestResponse(
                    entity.getId(),
                    entity.getProvider(),
                    entity.getProviderEnvelopeId(),
                    entity.getSigningUrl(),
                    entity.getStatus()
            );
        }

        Optional<EsignRequestEntity> existingByKey = esignRequestRepository.findByTenantIdAndIdempotencyKey(
                tenantId, idempotencyKey);
        if (existingByKey.isPresent()) {
            EsignRequestEntity entity = existingByKey.get();
            boolean sameRequest = entity.getChildId().equals(consent.getChildId())
                    && entity.getGuardianId().equals(consent.getGuardianId())
                    && entity.getDocType().equals(request.docType())
                    && entity.getDocVersion().equals(request.docVersion());
            if (!sameRequest) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used");
            }
            return new CreateEsignRequestResponse(
                    entity.getId(),
                    entity.getProvider(),
                    entity.getProviderEnvelopeId(),
                    entity.getSigningUrl(),
                    entity.getStatus()
            );
        }

        ProviderId providerId;
        try {
            providerId = ProviderId.fromString(request.provider());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported eSign provider");
        }
        EsignProvider provider = providerRegistry.getProvider(providerId);

        EsignCreateRequest createRequest = new EsignCreateRequest(
                tenantId,
                actorId,
                consent.getChildId(),
                consent.getGuardianId(),
                consent.getConsentId(),
                request.docType(),
                request.docVersion(),
                idempotencyKey,
                request.returnUrl()
        );

        EsignCreateRequestResult result = provider.createSigningRequest(createRequest);

        EsignRequestEntity entity = new EsignRequestEntity();
        entity.setTenantId(tenantId);
        entity.setChildId(consent.getChildId());
        entity.setGuardianId(consent.getGuardianId());
        entity.setConsentId(consent.getConsentId());
        entity.setDocType(request.docType());
        entity.setDocVersion(request.docVersion());
        entity.setProvider(providerId.name());
        entity.setProviderEnvelopeId(result.providerEnvelopeId());
        entity.setSigningUrl(result.signingUrl());
        entity.setStatus(result.status());
        entity.setIdempotencyKey(idempotencyKey);
        entity.setRequestPayloadSha256(result.requestPayloadSha256());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        entity = esignRequestRepository.save(entity);

        consent.setEsignRequestId(entity.getId());
        consentRepository.save(consent);

        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("childId", consent.getChildId().toString());
        payload.put("guardianId", consent.getGuardianId().toString());
        payload.put("consentId", consent.getConsentId().toString());
        payload.put("esignRequestId", entity.getId().toString());
        payload.put("provider", entity.getProvider());
        payload.put("providerEnvelopeId", entity.getProviderEnvelopeId());
        payload.put("status", entity.getStatus());
        payload.put("idempotencyKey", idempotencyKey);

        writeAudit(tenantId, actorId, "ESIGN_REQUEST_CREATED", "EsignRequest", entity.getId().toString(), payload);
        writeOutbox("esign.request_created", "EsignRequest", entity.getId().toString(), payload, idempotencyKey);

        logger.info("Created eSign request {} for consent {}", entity.getId(), consentId);

        return new CreateEsignRequestResponse(
                entity.getId(),
                entity.getProvider(),
                entity.getProviderEnvelopeId(),
                entity.getSigningUrl(),
                entity.getStatus()
        );
    }

    private void writeAudit(UUID tenantId, UUID actorId, String action, String entityType, String entityId, Map<String, ?> details) {
        try {
            String hash = hashPayload(details.toString());
            AuditEvent event = AuditEvent.builder()
                    .tenantId(tenantId)
                    .actorId(actorId)
                    .actorType(actorId != null ? AuditEvent.ActorType.USER : AuditEvent.ActorType.SYSTEM)
                    .service("children-guardian-service")
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .payloadHash(hash)
                    .build();
            auditWriter.write(event);
        } catch (Exception e) {
            logger.error("Failed to write audit", e);
        }
    }

    private void writeOutbox(String eventType, String entityType, String entityId, Map<String, ?> payload, String idempotencyKey) {
        try {
            EventEnvelopeV1 event = EventFactory.create(
                    eventType,
                    "children-guardian-service",
                    entityType,
                    entityId,
                    payload,
                    idempotencyKey
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
        } catch (Exception e) {
            return "";
        }
    }
}
