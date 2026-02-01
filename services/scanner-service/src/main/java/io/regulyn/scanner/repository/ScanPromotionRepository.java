package io.regulyn.scanner.repository;

import io.regulyn.scanner.model.ScanPromotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScanPromotionRepository extends JpaRepository<ScanPromotion, UUID> {

    Optional<ScanPromotion> findByTenantIdAndRunId(UUID tenantId, UUID runId);

    boolean existsByTenantIdAndRunId(UUID tenantId, UUID runId);
}
