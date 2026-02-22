package com.regulyn.consent.repository;

import com.regulyn.consent.entity.ReconsentRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconsentRequirementRepository extends JpaRepository<ReconsentRequirement, UUID> {
    Optional<ReconsentRequirement> findByTenantIdAndDataPrincipalIdAndRequiredPurposeVersionId(
            UUID tenantId,
            UUID dataPrincipalId,
            UUID requiredPurposeVersionId);

    List<ReconsentRequirement> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, com.regulyn.consent.entity.ReconsentStatus status);
}
