package com.regulyn.retention.repository;

import com.regulyn.retention.entity.DeletionRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeletionRequestRepository extends JpaRepository<DeletionRequest, UUID> {

    Optional<DeletionRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<DeletionRequest> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    Page<DeletionRequest> findByTenantId(UUID tenantId, Pageable pageable);

    Page<DeletionRequest> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    @Query("SELECT d FROM DeletionRequest d WHERE d.dueAt < :now AND d.closedAt IS NULL")
    List<DeletionRequest> findOverdueDeletionRequests(Instant now);
}
