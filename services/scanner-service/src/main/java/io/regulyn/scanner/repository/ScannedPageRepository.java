package io.regulyn.scanner.repository;

import io.regulyn.scanner.model.ScannedPageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScannedPageRepository extends JpaRepository<ScannedPageEntity, UUID> {

    List<ScannedPageEntity> findByTenantIdAndRunId(UUID tenantId, UUID runId);
}