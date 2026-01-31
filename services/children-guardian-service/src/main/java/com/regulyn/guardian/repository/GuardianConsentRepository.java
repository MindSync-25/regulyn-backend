package com.regulyn.guardian.repository;

import com.regulyn.guardian.entity.GuardianConsent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GuardianConsentRepository extends JpaRepository<GuardianConsent, UUID> {

    Optional<GuardianConsent> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<GuardianConsent> findByTenantIdAndChildProfileId(UUID tenantId, UUID childProfileId, Pageable pageable);

    Page<GuardianConsent> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
}
