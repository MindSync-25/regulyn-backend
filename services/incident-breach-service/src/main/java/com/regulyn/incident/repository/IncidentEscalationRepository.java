package com.regulyn.incident.repository;

import com.regulyn.incident.entity.IncidentEscalationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IncidentEscalationRepository extends JpaRepository<IncidentEscalationEntity, UUID> {
    boolean existsByIncidentIdAndThresholdHours(UUID incidentId, Integer thresholdHours);

    Optional<IncidentEscalationEntity> findByIncidentIdAndThresholdHours(UUID incidentId, Integer thresholdHours);
}
