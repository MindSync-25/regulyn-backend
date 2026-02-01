package com.regulyn.guardian.repository;

import com.regulyn.guardian.entity.Guardian;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GuardianRepository extends JpaRepository<Guardian, UUID> {
    
    Optional<Guardian> findByTenantIdAndGuardianId(UUID tenantId, UUID guardianId);
    
    List<Guardian> findByTenantIdAndChildId(UUID tenantId, UUID childId);
    
    Optional<Guardian> findByTenantIdAndChildIdAndGuardianEmail(UUID tenantId, UUID childId, String guardianEmail);
    
    boolean existsByTenantIdAndChildIdAndStatus(UUID tenantId, UUID childId, String status);
}
