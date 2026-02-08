package com.regulyn.incident.repository;

import com.regulyn.incident.entity.NoticeApprovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NoticeApprovalRepository extends JpaRepository<NoticeApprovalEntity, UUID> {
    boolean existsByApprovalRequestId(UUID approvalRequestId);

    Optional<NoticeApprovalEntity> findByApprovalRequestId(UUID approvalRequestId);

    Optional<NoticeApprovalEntity> findByDraftId(UUID draftId);
}
