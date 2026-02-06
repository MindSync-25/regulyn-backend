package io.regulyn.connector.repository;

import io.regulyn.connector.model.ConnectorRun;
import io.regulyn.connector.model.ConnectorRun.RunStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for ConnectorRun entities.
 */
@Repository
public interface ConnectorRunRepository extends JpaRepository<ConnectorRun, UUID> {

    /**
     * Find runs ready for retry.
     * Returns runs with status FAILED_RETRYABLE and next_retry_at <= now.
     * 
     * @param now Current timestamp
     * @return List of retryable runs
     */
    @Query("SELECT r FROM ConnectorRun r WHERE r.status = 'FAILED_RETRYABLE' AND r.nextRetryAt <= :now")
    List<ConnectorRun> findRetryableRuns(@Param("now") Instant now);

    /**
     * Find recent runs for a connector, ordered by creation time descending.
     * 
     * @param tenantId Tenant ID
     * @param connectorId Connector ID
     * @return List of recent runs
     */
    List<ConnectorRun> findByTenantIdAndConnectorIdOrderByCreatedAtDesc(UUID tenantId, UUID connectorId);

    /**
     * Find recent runs for a connector with paging.
     */
    List<ConnectorRun> findByConnectorIdOrderByCreatedAtDesc(UUID connectorId, Pageable pageable);

    /**
     * Find stuck runs that have been RUNNING since before the given time.
     */
    List<ConnectorRun> findByStatusAndStartedAtBefore(RunStatus status, Instant beforeTime);

    /**
     * Count runs for a schedule within a time window (for idempotency check).
     * 
     * @param scheduleId Schedule ID
     * @param start Start of time window
     * @param end End of time window
     * @return Count of runs in the window
     */
    long countByScheduleIdAndCreatedAtBetween(UUID scheduleId, Instant start, Instant end);
}
