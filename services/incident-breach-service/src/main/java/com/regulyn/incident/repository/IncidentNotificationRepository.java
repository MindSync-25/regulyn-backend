package com.regulyn.incident.repository;

import com.regulyn.incident.entity.IncidentNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentNotificationRepository extends JpaRepository<IncidentNotification, UUID> {
    List<IncidentNotification> findByTenantIdAndIncidentId(UUID tenantId, UUID incidentId);
    Optional<IncidentNotification> findByTenantIdAndNotificationId(UUID tenantId, UUID notificationId);
}
