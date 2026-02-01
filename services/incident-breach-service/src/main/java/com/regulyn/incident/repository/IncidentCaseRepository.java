package com.regulyn.incident.repository;

import com.regulyn.incident.entity.IncidentCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentCaseRepository extends JpaRepository<IncidentCase, UUID> {

    Optional<IncidentCase> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<IncidentCase> findByTenantId(UUID tenantId, Pageable pageable);

    Page<IncidentCase> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    Page<IncidentCase> findByTenantIdAndSeverity(UUID tenantId, String severity, Pageable pageable);

    Page<IncidentCase> findByTenantIdAndStatusAndSeverity(UUID tenantId, String status, String severity, Pageable pageable);

    @Query("SELECT i FROM IncidentCase i WHERE i.notifyDueAt < :now AND i.status != 'NOTIFIED' AND i.notifyOverdue = false")
    List<IncidentCase> findOverdueIncidents(Instant now);
}
