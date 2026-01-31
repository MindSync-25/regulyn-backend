package com.regulyn.guardian.repository;

import com.regulyn.guardian.entity.GuardianVerification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GuardianVerificationRepository extends JpaRepository<GuardianVerification, UUID> {

    Optional<GuardianVerification> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<GuardianVerification> findByTenantIdAndChildProfileId(UUID tenantId, UUID childProfileId, Pageable pageable);

    Page<GuardianVerification> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
}
