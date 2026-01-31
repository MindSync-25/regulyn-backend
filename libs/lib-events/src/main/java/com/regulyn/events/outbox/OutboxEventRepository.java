package com.regulyn.events.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for OutboxEvent entities
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Find pending events ready for publishing
     * @param status the status to filter by
     * @param beforeTime events with next_attempt_at before this time
     * @param limit maximum number of events to return
     * @return list of pending events
     */
    @Query("SELECT o FROM OutboxEvent o WHERE o.status = :status AND o.nextAttemptAt <= :beforeTime ORDER BY o.occurredAt ASC")
    List<OutboxEvent> findPendingEvents(
        @Param("status") OutboxStatus status, 
        @Param("beforeTime") Instant beforeTime);

    /**
     * Find events by tenant and occurred after a specific time
     */
    List<OutboxEvent> findByTenantIdAndOccurredAtAfterOrderByOccurredAtAsc(UUID tenantId, Instant after);

    /**
     * Find events by entity type and entity ID
     */
    List<OutboxEvent> findByEntityTypeAndEntityIdOrderByOccurredAtAsc(String entityType, String entityId);
}
