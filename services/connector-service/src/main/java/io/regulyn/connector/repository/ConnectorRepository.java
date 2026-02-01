package io.regulyn.connector.repository;

import io.regulyn.connector.model.Connector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConnectorRepository extends JpaRepository<Connector, UUID> {

    Optional<Connector> findByTenantIdAndConnectorId(UUID tenantId, UUID connectorId);

    List<Connector> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<Connector> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    List<Connector> findByTenantIdAndConnectorTypeOrderByCreatedAtDesc(UUID tenantId, String connectorType);

    boolean existsByTenantIdAndConnectorName(UUID tenantId, String connectorName);
}
