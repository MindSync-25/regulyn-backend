package io.regulyn.connector.repository;

import io.regulyn.connector.model.ConnectorRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConnectorRunRepository extends JpaRepository<ConnectorRun, UUID> {
    
    List<ConnectorRun> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);
    
    List<ConnectorRun> findByScheduleIdOrderByCreatedAtDesc(UUID scheduleId);
    
    Optional<ConnectorRun> findByScheduleIdAndScheduledFireTime(UUID scheduleId, Instant scheduledFireTime);
    
    Optional<ConnectorRun> findByIdempotencyKey(String idempotencyKey);
    
    List<ConnectorRun> findByConnectorIdAndCreatedAtAfter(UUID connectorId, Instant after);
}
