package io.regulyn.scanner.repository;

import io.regulyn.scanner.model.ScanRunEvidenceRefEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScanRunEvidenceRefRepository extends JpaRepository<ScanRunEvidenceRefEntity, UUID> {
	Optional<ScanRunEvidenceRefEntity> findByTenantIdAndRunId(UUID tenantId, UUID runId);
}