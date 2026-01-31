package com.regulyn.dsar.repository;

import com.regulyn.dsar.entity.DsarRequestEntity;
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
public interface DsarRequestRepository extends JpaRepository<DsarRequestEntity, UUID> {
    
    Optional<DsarRequestEntity> findByRequestIdAndTenantId(String requestId, UUID tenantId);
    
    Page<DsarRequestEntity> findByTenantId(UUID tenantId, Pageable pageable);
    
    Page<DsarRequestEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
    
    @Query("SELECT d FROM DsarRequestEntity d WHERE d.dueAt < :now AND d.closedAt IS NULL")
    List<DsarRequestEntity> findOverdueDsarRequests(Instant now);
}
