package io.regulyn.scanner.repository;

import io.regulyn.scanner.model.RemediationTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RemediationTaskRepository extends JpaRepository<RemediationTaskEntity, UUID> {

    Optional<RemediationTaskEntity> findFirstByTenantIdAndFindingFingerprintAndStatusIn(
        UUID tenantId,
        String findingFingerprint,
        Collection<RemediationTaskEntity.Status> statuses
    );

    boolean existsByTenantIdAndFindingFingerprintAndStatusIn(
        UUID tenantId,
        String findingFingerprint,
        Collection<RemediationTaskEntity.Status> statuses
    );

    Optional<RemediationTaskEntity> findByTenantIdAndTaskId(UUID tenantId, UUID taskId);

    List<RemediationTaskEntity> findByTenantIdAndRunId(UUID tenantId, UUID runId);

    @Query("SELECT t FROM RemediationTaskEntity t WHERE t.tenantId = :tenantId " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (:sourceId IS NULL OR t.sourceId = :sourceId) " +
           "AND (:runId IS NULL OR t.runId = :runId) " +
           "AND (:severity IS NULL OR t.severity = :severity)")
    Page<RemediationTaskEntity> findByFilters(
        @Param("tenantId") UUID tenantId,
        @Param("status") RemediationTaskEntity.Status status,
        @Param("sourceId") UUID sourceId,
        @Param("runId") UUID runId,
        @Param("severity") RemediationTaskEntity.Severity severity,
        Pageable pageable
    );
}