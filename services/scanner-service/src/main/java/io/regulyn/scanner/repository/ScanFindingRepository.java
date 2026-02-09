package io.regulyn.scanner.repository;

import io.regulyn.scanner.model.ScanFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScanFindingRepository extends JpaRepository<ScanFinding, UUID> {

    List<ScanFinding> findByTenantIdAndRunIdOrderByCreatedAtDesc(UUID tenantId, UUID runId);

    List<ScanFinding> findByTenantIdAndRunIdAndFindingFingerprintIsNotNullOrderByCreatedAtDesc(UUID tenantId, UUID runId);

    Optional<ScanFinding> findByTenantIdAndFindingId(UUID tenantId, UUID findingId);

    @Query("SELECT f FROM ScanFinding f WHERE f.tenantId = :tenantId AND f.runId = :runId " +
           "AND f.findingType = 'RETENTION_CANDIDATE' AND f.riskLevel IN :riskLevels " +
           "ORDER BY f.createdAt DESC")
    List<ScanFinding> findRetentionCandidates(@Param("tenantId") UUID tenantId,
                                               @Param("runId") UUID runId,
                                               @Param("riskLevels") List<String> riskLevels);

    long countByTenantIdAndRunId(UUID tenantId, UUID runId);
}
