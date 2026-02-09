package io.regulyn.scanner.repository;

import io.regulyn.scanner.model.RemediationTaskEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RemediationTaskEventRepository extends JpaRepository<RemediationTaskEventEntity, UUID> {

    List<RemediationTaskEventEntity> findByTenantIdAndTaskIdOrderByCreatedAtAsc(UUID tenantId, UUID taskId);

    Optional<RemediationTaskEventEntity> findTopByTenantIdAndTaskIdAndEventTypeAndToStatusAndActorUserIdAndNotesOrderByCreatedAtDesc(
        UUID tenantId,
        UUID taskId,
        String eventType,
        String toStatus,
        UUID actorUserId,
        String notes
    );
}