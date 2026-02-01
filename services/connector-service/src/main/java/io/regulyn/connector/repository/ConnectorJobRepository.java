package io.regulyn.connector.repository;

import io.regulyn.connector.model.ConnectorJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConnectorJobRepository extends JpaRepository<ConnectorJob, UUID> {

    Optional<ConnectorJob> findByTenantIdAndJobId(UUID tenantId, UUID jobId);

    Optional<ConnectorJob> findByTenantIdAndSubjectIdAndConnectorIdAndTargetIdAndIdempotencyKey(
            UUID tenantId, UUID subjectId, UUID connectorId, UUID targetId, String idempotencyKey);

    Page<ConnectorJob> findByTenantIdOrderByQueuedAtDesc(UUID tenantId, Pageable pageable);

    Page<ConnectorJob> findByTenantIdAndStatusOrderByQueuedAtDesc(UUID tenantId, String status, Pageable pageable);

    Page<ConnectorJob> findByTenantIdAndJobTypeOrderByQueuedAtDesc(UUID tenantId, String jobType, Pageable pageable);

    Page<ConnectorJob> findByTenantIdAndConnectorIdOrderByQueuedAtDesc(UUID tenantId, UUID connectorId, Pageable pageable);

    Page<ConnectorJob> findByTenantIdAndSubjectIdOrderByQueuedAtDesc(UUID tenantId, UUID subjectId, Pageable pageable);

    Page<ConnectorJob> findByTenantIdAndRequestRefOrderByQueuedAtDesc(UUID tenantId, String requestRef, Pageable pageable);

    List<ConnectorJob> findByTenantIdOrderByQueuedAtDesc(UUID tenantId);
}
