package com.regulyn.nominee.service;

import com.regulyn.common.enums.NomineeStatus;
import com.regulyn.nominee.dto.RegisterNomineeRequest;
import com.regulyn.nominee.dto.NomineeResponse;
import com.regulyn.nominee.dto.VerifyNomineeRequest;
import com.regulyn.nominee.entity.Nominee;
import com.regulyn.nominee.repository.NomineeRepository;
import com.regulyn.events.publisher.OutboxEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NomineeService {

    private static final Logger log = LoggerFactory.getLogger(NomineeService.class);

    private final NomineeRepository nomineeRepository;
    private final NomineeWorkflowValidator workflowValidator;
    private final OutboxEventPublisher eventPublisher;

    public NomineeService(NomineeRepository nomineeRepository,
                          NomineeWorkflowValidator workflowValidator,
                          OutboxEventPublisher eventPublisher) {
        this.nomineeRepository = nomineeRepository;
        this.workflowValidator = workflowValidator;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public NomineeResponse registerNominee(UUID tenantId, UUID dataPrincipalId, RegisterNomineeRequest request) {
        log.info("Registering nominee for tenant={}, principal={}", tenantId, dataPrincipalId);

        Nominee nominee = new Nominee();
        nominee.setTenantId(tenantId);
        nominee.setDataPrincipalId(dataPrincipalId);
        nominee.setNomineeName(request.getNomineeName());
        // nomineeContact stored as email for now
        if (request.getNomineeEmail() != null) {
            nominee.setNomineeContact(request.getNomineeEmail());
        }
        nominee.setRelationship(request.getRelationship().name());
        nominee.setScope(request.getScope().name());
        nominee.setStatus("PENDING");
        nominee.setRegisteredAt(Instant.now());

        nominee = nomineeRepository.save(nominee);

        eventPublisher.publish("nominee.registered", nominee.getId().toString(), nominee);

        log.info("Nominee registered: id={}", nominee.getId());
        return toResponse(nominee);
    }

    @Transactional
    public NomineeResponse verifyNominee(UUID nomineeId, VerifyNomineeRequest request) {
        log.info("Verifying nominee: id={}, method={}", nomineeId, request.getMethod());

        Nominee nominee = nomineeRepository.findById(nomineeId)
            .orElseThrow(() -> new IllegalArgumentException("Nominee not found: " + nomineeId));

        // Skip workflow validation for now
        nominee.setStatus("VERIFIED");
        nominee.setVerificationMethod(request.getMethod().name());
        nominee.setVerifiedAt(Instant.now());

        nominee = nomineeRepository.save(nominee);

        eventPublisher.publish("nominee.verified", nominee.getId().toString(), nominee);

        log.info("Nominee verified: id={}", nominee.getId());
        return toResponse(nominee);
    }

    @Transactional
    public void disableNominee(UUID nomineeId, UUID disabledBy) {
        log.info("Disabling nominee: id={}, by={}", nomineeId, disabledBy);

        Nominee nominee = nomineeRepository.findById(nomineeId)
            .orElseThrow(() -> new IllegalArgumentException("Nominee not found: " + nomineeId));

        // Skip workflow validation for now
        nominee.setStatus("DISABLED");
        nominee.setDisabledAt(Instant.now());
        nominee.setDisabledBy(disabledBy);

        nomineeRepository.save(nominee);

        eventPublisher.publish("nominee.disabled", nominee.getId().toString(), nominee);

        log.info("Nominee disabled: id={}", nominee.getId());
    }

    public NomineeResponse getNominee(UUID nomineeId) {
        Nominee nominee = nomineeRepository.findById(nomineeId)
            .orElseThrow(() -> new IllegalArgumentException("Nominee not found: " + nomineeId));
        return toResponse(nominee);
    }

    public List<NomineeResponse> getNomineesByPrincipal(UUID tenantId, UUID dataPrincipalId) {
        return nomineeRepository.findByTenantIdAndDataPrincipalId(tenantId, dataPrincipalId)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private NomineeResponse toResponse(Nominee nominee) {
        NomineeResponse response = new NomineeResponse();
        response.setNomineeId(nominee.getId());
        response.setDataPrincipalId(nominee.getDataPrincipalId());
        response.setNomineeName(nominee.getNomineeName());
        response.setNomineeEmail(nominee.getNomineeContact()); // Contact stored as email
        response.setRelationship(nominee.getRelationship());
        response.setScope(nominee.getScope());
        response.setStatus(nominee.getStatus());
        response.setVerifiedAt(nominee.getVerifiedAt());
        return response;
    }
}
