package io.regulyn.connector.repository;

import io.regulyn.connector.model.ConnectorExport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConnectorExportRepository extends JpaRepository<ConnectorExport, UUID> {

    Optional<ConnectorExport> findByTenantIdAndExportId(UUID tenantId, UUID exportId);
}
