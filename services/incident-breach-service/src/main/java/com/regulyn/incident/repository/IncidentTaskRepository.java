package com.regulyn.incident.repository;

import com.regulyn.incident.entity.IncidentTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentTaskRepository extends JpaRepository<IncidentTask, UUID> {
    List<IncidentTask> findByTenantIdAndIncidentId(UUID tenantId, UUID incidentId);
}
