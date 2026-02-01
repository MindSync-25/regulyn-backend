package com.regulyn.incident.repository;

import com.regulyn.incident.entity.IncidentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IncidentStatusHistoryRepository extends JpaRepository<IncidentStatusHistory, UUID> {
}
