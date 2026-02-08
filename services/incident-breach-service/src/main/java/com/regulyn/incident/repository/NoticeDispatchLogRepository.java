package com.regulyn.incident.repository;

import com.regulyn.incident.entity.NoticeDispatchLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoticeDispatchLogRepository extends JpaRepository<NoticeDispatchLogEntity, UUID> {
    boolean existsByDraftIdAndRecipientIdentifierAndChannel(UUID draftId, String recipientIdentifier, String channel);

    Optional<NoticeDispatchLogEntity> findByDraftIdAndRecipientIdentifierAndChannel(UUID draftId, String recipientIdentifier, String channel);

    List<NoticeDispatchLogEntity> findByTenantIdAndDraftIdAndStatus(UUID tenantId, UUID draftId, String status);

    Optional<NoticeDispatchLogEntity> findByTenantIdAndNotificationRequestId(UUID tenantId, String notificationRequestId);

    Optional<NoticeDispatchLogEntity> findByTenantIdAndProviderMessageId(UUID tenantId, String providerMessageId);

    List<NoticeDispatchLogEntity> findByTenantIdAndIdIn(UUID tenantId, List<UUID> ids);

        List<NoticeDispatchLogEntity> findByDraftId(UUID draftId);

    @Modifying
    @Query("update NoticeDispatchLogEntity l "
            + "set l.status = :status, "
            + "l.notificationRequestId = :notificationRequestId, "
            + "l.providerMessageId = :providerMessageId, "
            + "l.sentAt = :sentAt, "
            + "l.lastStatusAt = :lastStatusAt "
            + "where l.id = :id and l.tenantId = :tenantId and l.status = :expectedStatus")
    int updateStatusIfMatches(@Param("tenantId") UUID tenantId,
                              @Param("id") UUID id,
                              @Param("expectedStatus") String expectedStatus,
                              @Param("status") String status,
                              @Param("notificationRequestId") String notificationRequestId,
                              @Param("providerMessageId") String providerMessageId,
                              @Param("sentAt") Instant sentAt,
                              @Param("lastStatusAt") Instant lastStatusAt);
}
