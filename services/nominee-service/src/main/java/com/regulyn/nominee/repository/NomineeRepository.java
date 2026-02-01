package com.regulyn.nominee.repository;

import com.regulyn.nominee.entity.Nominee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NomineeRepository extends JpaRepository<Nominee, UUID> {

    Optional<Nominee> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Nominee> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Nominee> findByTenantIdAndDataPrincipalId(UUID tenantId, UUID dataPrincipalId, Pageable pageable);
    
    List<Nominee> findByTenantIdAndDataPrincipalId(UUID tenantId, UUID dataPrincipalId);

    Page<Nominee> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
}
