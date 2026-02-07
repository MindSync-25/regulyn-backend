package com.regulyn.notification.repository;

import com.regulyn.notification.entity.NotificationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationMessageRepository extends JpaRepository<NotificationMessage, UUID> {
    
    @Query("SELECT m FROM NotificationMessage m WHERE m.tenantId = :tenantId AND m.messageHashHex = :messageHashHex")
    Optional<NotificationMessage> findByTenantIdAndMessageHashHex(
        @Param("tenantId") UUID tenantId,
        @Param("messageHashHex") String messageHashHex
    );

    @Query("SELECT m FROM NotificationMessage m WHERE m.tenantId = :tenantId AND m.id = :id")
    Optional<NotificationMessage> findByTenantIdAndId(
        @Param("tenantId") UUID tenantId,
        @Param("id") UUID id
    );

    @Query(
        value = """
            SELECT * FROM notification.notification_messages
            WHERE status IN ('FAILED_RETRYABLE','QUEUED')
              AND (next_attempt_at IS NULL OR next_attempt_at <= now())
              AND attempt_count < max_attempts
            ORDER BY COALESCE(next_attempt_at, created_at) ASC
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
            """,
        nativeQuery = true
    )
    List<NotificationMessage> findDueMessagesForRetry(@Param("batchSize") int batchSize);
}
