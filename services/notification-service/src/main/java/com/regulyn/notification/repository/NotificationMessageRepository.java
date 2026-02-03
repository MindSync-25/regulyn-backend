package com.regulyn.notification.repository;

import com.regulyn.notification.entity.NotificationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationMessageRepository extends JpaRepository<NotificationMessage, UUID> {
    
    List<NotificationMessage> findByTenantIdAndRequestId(String tenantId, UUID requestId);
    
    List<NotificationMessage> findByTenantIdAndRecipientId(String tenantId, String recipientId);
    
    List<NotificationMessage> findByStatus(NotificationMessage.MessageStatus status);
    
    @Query("SELECT m FROM NotificationMessage m WHERE m.status IN ('FAILED_RETRYABLE') AND m.nextRetryAt <= :now AND m.attemptCount < m.maxAttempts")
    List<NotificationMessage> findMessagesReadyForRetry(@Param("now") Instant now);
    
    Optional<NotificationMessage> findByProviderMessageId(String providerMessageId);
    
    Optional<NotificationMessage> findByTenantIdAndRecipientAddressAndMessageHash(
        String tenantId, 
        String recipientAddress, 
        String messageHash
    );
}
