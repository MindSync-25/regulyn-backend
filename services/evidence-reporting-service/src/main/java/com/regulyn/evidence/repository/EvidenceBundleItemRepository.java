package com.regulyn.evidence.repository;

import com.regulyn.evidence.entity.EvidenceBundleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EvidenceBundleItemRepository extends JpaRepository<EvidenceBundleItem, UUID> {
    
    List<EvidenceBundleItem> findByBundleIdAndTenantId(UUID bundleId, UUID tenantId);
    
    List<EvidenceBundleItem> findByEvidenceIdAndTenantId(UUID evidenceId, UUID tenantId);
    
    List<EvidenceBundleItem> findByArtifactIdAndTenantId(UUID artifactId, UUID tenantId);
}
