package io.regulyn.scanner.repository;

import io.regulyn.scanner.model.ScanRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScanRunRepository extends JpaRepository<ScanRun, UUID> {

    Optional<ScanRun> findByTenantIdAndRunId(UUID tenantId, UUID runId);

    Page<ScanRun> findByTenantIdOrderByQueuedAtDesc(UUID tenantId, Pageable pageable);

    Page<ScanRun> findByTenantIdAndStatusOrderByQueuedAtDesc(UUID tenantId, String status, Pageable pageable);

    Page<ScanRun> findByTenantIdAndSourceIdOrderByQueuedAtDesc(UUID tenantId, UUID sourceId, Pageable pageable);

    List<ScanRun> findByTenantIdAndSourceIdAndStatusOrderByQueuedAtDesc(UUID tenantId, UUID sourceId, String status);
}
