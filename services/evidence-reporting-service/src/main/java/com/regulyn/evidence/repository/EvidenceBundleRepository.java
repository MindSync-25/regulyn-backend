package com.regulyn.evidence.repository;

import com.regulyn.evidence.entity.EvidenceBundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EvidenceBundleRepository extends JpaRepository<EvidenceBundle, UUID> {
    
    Optional<EvidenceBundle> findByBundleIdAndTenantId(UUID bundleId, UUID tenantId);
    
    List<EvidenceBundle> findByTenantIdAndReferenceTypeAndReferenceId(
        UUID tenantId, 
        String referenceType, 
        String referenceId
    );
    
    List<EvidenceBundle> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
