package io.regulyn.scanner.repository;

import io.regulyn.scanner.model.ScannerExport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScannerExportRepository extends JpaRepository<ScannerExport, UUID> {

    Optional<ScannerExport> findByTenantIdAndExportId(UUID tenantId, UUID exportId);
}
