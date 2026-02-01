package com.regulyn.nominee.repository;

import com.regulyn.nominee.entity.NomineeClaim;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NomineeClaimRepository extends JpaRepository<NomineeClaim, UUID> {

    Optional<NomineeClaim> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<NomineeClaim> findByTenantId(UUID tenantId, Pageable pageable);

    Page<NomineeClaim> findByTenantIdAndNomineeId(UUID tenantId, UUID nomineeId, Pageable pageable);
    
    List<NomineeClaim> findByNomineeId(UUID nomineeId);

    Page<NomineeClaim> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
}
