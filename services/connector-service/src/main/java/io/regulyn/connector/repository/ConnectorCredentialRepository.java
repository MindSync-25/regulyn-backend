package io.regulyn.connector.repository;

import io.regulyn.connector.model.ConnectorCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConnectorCredentialRepository extends JpaRepository<ConnectorCredential, UUID> {
    
    Optional<ConnectorCredential> findByConnectorIdAndCredentialType(UUID connectorId, String credentialType);
    
    Optional<ConnectorCredential> findByTenantIdAndConnectorIdAndCredentialType(UUID tenantId, UUID connectorId, String credentialType);
    
    boolean existsByConnectorIdAndCredentialType(UUID connectorId, String credentialType);
    
    void deleteByConnectorIdAndCredentialType(UUID connectorId, String credentialType);
}
