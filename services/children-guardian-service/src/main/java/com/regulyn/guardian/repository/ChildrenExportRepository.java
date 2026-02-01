package com.regulyn.guardian.repository;

import com.regulyn.guardian.entity.ChildrenExport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChildrenExportRepository extends JpaRepository<ChildrenExport, UUID> {
    
    Optional<ChildrenExport> findByTenantIdAndExportId(UUID tenantId, UUID exportId);
    
    Optional<ChildrenExport> findByBundleId(UUID bundleId);
}
