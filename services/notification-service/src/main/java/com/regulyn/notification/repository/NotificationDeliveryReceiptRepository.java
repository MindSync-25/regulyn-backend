package com.regulyn.notification.repository;

import com.regulyn.notification.entity.NotificationDeliveryReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationDeliveryReceiptRepository extends JpaRepository<NotificationDeliveryReceipt, UUID> {
    
    @Query("SELECT r FROM NotificationDeliveryReceipt r WHERE r.tenantId = :tenantId AND r.payloadHashHex = :payloadHashHex")
    Optional<NotificationDeliveryReceipt> findByTenantIdAndPayloadHashHex(
        @Param("tenantId") UUID tenantId,
        @Param("payloadHashHex") String payloadHashHex
    );
    
    @Query("SELECT r FROM NotificationDeliveryReceipt r WHERE r.tenantId = :tenantId AND r.notificationMessageId = :messageId")
    List<NotificationDeliveryReceipt> findByTenantIdAndNotificationMessageId(
        @Param("tenantId") UUID tenantId,
        @Param("messageId") UUID messageId
    );
}
