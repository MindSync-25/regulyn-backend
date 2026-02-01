package io.regulyn.connector.repository;

import io.regulyn.connector.model.ConnectorJobLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConnectorJobLogRepository extends JpaRepository<ConnectorJobLog, UUID> {

    List<ConnectorJobLog> findByTenantIdAndJobIdOrderByCreatedAtAsc(UUID tenantId, UUID jobId);
}
