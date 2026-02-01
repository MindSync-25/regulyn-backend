package com.regulyn.nominee.service;

import com.regulyn.nominee.dto.RightsGrantResponse;
import com.regulyn.nominee.entity.RightsGrant;
import com.regulyn.nominee.repository.RightsGrantRepository;
import com.regulyn.events.publisher.OutboxEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RightsService {

    private static final Logger log = LoggerFactory.getLogger(RightsService.class);

    private final RightsGrantRepository grantsRepository;
    private final OutboxEventPublisher eventPublisher;

    public RightsService(RightsGrantRepository grantsRepository,
                         OutboxEventPublisher eventPublisher) {
        this.grantsRepository = grantsRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public RightsGrantResponse grantRights(UUID tenantId, UUID dataPrincipalId, UUID nomineeId, 
                                           String scope, LocalDate validFrom, LocalDate validTo,
                                           UUID claimId, UUID grantedBy) {
        log.info("Granting rights: tenant={}, principal={}, nominee={}, scope={}", 
            tenantId, dataPrincipalId, nomineeId, scope);

        RightsGrant grant = new RightsGrant();
        grant.setTenantId(tenantId);
        grant.setDataPrincipalId(dataPrincipalId);
        grant.setNomineeId(nomineeId);
        grant.setScope(scope);
        grant.setStatus("ACTIVE");
        grant.setGrantedAt(Instant.now());
        grant.setGrantedBy(grantedBy);
        grant.setValidFrom(validFrom);
        grant.setValidTo(validTo);
        grant.setClaimId(claimId);

        grant = grantsRepository.save(grant);

        eventPublisher.publish("rights.granted", grant.getId().toString(), grant);

        log.info("Rights granted: grantId={}", grant.getId());
        return toResponse(grant);
    }

    @Transactional
    public void revokeRights(UUID grantId, UUID revokedBy) {
        log.info("Revoking rights: grantId={}, by={}", grantId, revokedBy);

        RightsGrant grant = grantsRepository.findById(grantId)
            .orElseThrow(() -> new IllegalArgumentException("Rights grant not found: " + grantId));

        grant.setStatus("REVOKED");
        grant.setRevokedAt(Instant.now());
        grant.setRevokedBy(revokedBy);

        grantsRepository.save(grant);

        eventPublisher.publish("rights.revoked", grant.getId().toString(), grant);

        log.info("Rights revoked: grantId={}", grantId);
    }

    public List<RightsGrantResponse> getRightsByPrincipal(UUID tenantId, UUID dataPrincipalId) {
        return grantsRepository.findByTenantIdAndDataPrincipalId(tenantId, dataPrincipalId)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public List<RightsGrantResponse> getRightsByNominee(UUID nomineeId) {
        return grantsRepository.findByNomineeId(nomineeId)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private RightsGrantResponse toResponse(RightsGrant grant) {
        RightsGrantResponse response = new RightsGrantResponse();
        response.setGrantId(grant.getId());
        response.setDataPrincipalId(grant.getDataPrincipalId());
        response.setNomineeId(grant.getNomineeId());
        response.setScope(grant.getScope());
        response.setStatus(grant.getStatus());
        response.setGrantedAt(grant.getGrantedAt());
        response.setGrantedBy(grant.getGrantedBy());
        response.setValidFrom(grant.getValidFrom());
        response.setValidTo(grant.getValidTo());
        response.setClaimId(grant.getClaimId());
        response.setRevokedAt(grant.getRevokedAt());
        response.setRevokedBy(grant.getRevokedBy());
        return response;
    }
}
