package com.regulyn.incident.repository;

import com.regulyn.incident.entity.NoticeDraftEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoticeDraftRepository extends JpaRepository<NoticeDraftEntity, UUID> {
    boolean existsByIncidentIdAndTemplateVersionIdAndNoticeType(UUID incidentId, UUID templateVersionId, String noticeType);

    Optional<NoticeDraftEntity> findByIncidentIdAndTemplateVersionIdAndNoticeType(UUID incidentId, UUID templateVersionId, String noticeType);

    Optional<NoticeDraftEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<NoticeDraftEntity> findByIncidentId(UUID incidentId);
}
