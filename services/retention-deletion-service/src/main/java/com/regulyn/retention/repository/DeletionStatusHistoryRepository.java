package com.regulyn.retention.repository;

import com.regulyn.retention.entity.DeletionStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeletionStatusHistoryRepository extends JpaRepository<DeletionStatusHistory, UUID> {

    List<DeletionStatusHistory> findByDeletionIdOrderByChangedAtDesc(UUID deletionId);

    List<DeletionStatusHistory> findByTenantIdAndDeletionIdOrderByChangedAtDesc(UUID tenantId, UUID deletionId);
}
