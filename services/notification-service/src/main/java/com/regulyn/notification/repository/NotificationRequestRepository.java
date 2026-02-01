package com.regulyn.notification.repository;

import com.regulyn.notification.entity.NotificationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRequestRepository extends JpaRepository<NotificationRequest, UUID> {
    
    @Query("SELECT r FROM NotificationRequest r WHERE r.tenantId = :tenantId AND r.requestRef = :requestRef")
    Optional<NotificationRequest> findByTenantIdAndRequestRef(
        @Param("tenantId") String tenantId, 
        @Param("requestRef") String requestRef
    );
}
