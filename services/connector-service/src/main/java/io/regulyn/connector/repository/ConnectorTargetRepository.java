package io.regulyn.connector.repository;

import io.regulyn.connector.model.ConnectorTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConnectorTargetRepository extends JpaRepository<ConnectorTarget, UUID> {

    Optional<ConnectorTarget> findByTenantIdAndTargetId(UUID tenantId, UUID targetId);

    List<ConnectorTarget> findByTenantIdAndConnectorIdOrderByCreatedAtDesc(UUID tenantId, UUID connectorId);

    boolean existsByTenantIdAndConnectorIdAndTargetKey(UUID tenantId, UUID connectorId, String targetKey);
}
