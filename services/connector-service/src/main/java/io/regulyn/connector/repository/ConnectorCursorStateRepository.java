package io.regulyn.connector.repository;

import io.regulyn.connector.model.ConnectorCursorState;
import io.regulyn.connector.model.ConnectorCursorState.JobType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ConnectorCursorState entities.
 */
@Repository
public interface ConnectorCursorStateRepository extends JpaRepository<ConnectorCursorState, UUID> {

    /**
     * Find cursor state by composite key.
     * 
     * @param tenantId Tenant ID
     * @param connectorId Connector ID
     * @param targetId Target ID (nullable)
     * @param jobType Job type
     * @return Optional containing the cursor state if found
     */
    Optional<ConnectorCursorState> findByTenantIdAndConnectorIdAndTargetIdAndJobType(
        UUID tenantId,
        UUID connectorId,
        UUID targetId,
        JobType jobType
    );
}
