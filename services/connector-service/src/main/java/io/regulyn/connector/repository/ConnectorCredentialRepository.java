package io.regulyn.connector.repository;

import io.regulyn.connector.model.ConnectorCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ConnectorCredential entities.
 */
@Repository
public interface ConnectorCredentialRepository extends JpaRepository<ConnectorCredential, UUID> {

    /**
     * Find credential by tenant and connector ID.
     * 
     * @param tenantId Tenant ID
     * @param connectorId Connector ID
     * @return Optional containing the credential if found
     */
    Optional<ConnectorCredential> findByTenantIdAndConnectorId(UUID tenantId, UUID connectorId);
}
