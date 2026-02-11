package com.regulyn.guardian.repository;

import com.regulyn.guardian.entity.ChildrenEvidenceExportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChildrenEvidenceExportRepository extends JpaRepository<ChildrenEvidenceExportEntity, UUID> {

    List<ChildrenEvidenceExportEntity> findByTenantIdAndChildIdOrderByCreatedAtDesc(UUID tenantId, UUID childId);

        java.util.Optional<ChildrenEvidenceExportEntity> findByTenantIdAndChildIdAndExportScopeAndIdempotencyKey(
            UUID tenantId,
            UUID childId,
            String exportScope,
            String idempotencyKey
        );
}
