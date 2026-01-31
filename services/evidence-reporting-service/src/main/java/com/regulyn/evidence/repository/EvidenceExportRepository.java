package com.regulyn.evidence.repository;

import com.regulyn.evidence.entity.EvidenceExport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EvidenceExportRepository extends JpaRepository<EvidenceExport, UUID> {
    
    Optional<EvidenceExport> findByExportIdAndTenantId(UUID exportId, UUID tenantId);
    
    List<EvidenceExport> findByBundleIdAndTenantId(UUID bundleId, UUID tenantId);
    
    List<EvidenceExport> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
