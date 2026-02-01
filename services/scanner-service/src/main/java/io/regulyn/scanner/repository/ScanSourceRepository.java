package io.regulyn.scanner.repository;

import io.regulyn.scanner.model.ScanSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScanSourceRepository extends JpaRepository<ScanSource, UUID> {

    Optional<ScanSource> findByTenantIdAndSourceId(UUID tenantId, UUID sourceId);

    List<ScanSource> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<ScanSource> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    List<ScanSource> findByTenantIdAndSourceTypeOrderByCreatedAtDesc(UUID tenantId, String sourceType);

    boolean existsByTenantIdAndSourceName(UUID tenantId, String sourceName);
}
