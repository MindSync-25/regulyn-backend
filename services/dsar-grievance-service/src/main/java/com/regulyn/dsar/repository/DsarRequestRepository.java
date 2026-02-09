package com.regulyn.dsar.repository;

import com.regulyn.dsar.entity.DsarRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DsarRequestRepository extends JpaRepository<DsarRequestEntity, UUID> {
    
    Optional<DsarRequestEntity> findByRequestIdAndTenantId(String requestId, UUID tenantId);
    
    Optional<DsarRequestEntity> findByRequestIdPkAndTenantId(UUID requestIdPk, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DsarRequestEntity d WHERE d.requestIdPk = :dsarId AND d.tenantId = :tenantId")
    Optional<DsarRequestEntity> findByRequestIdPkAndTenantIdForUpdate(
        @Param("dsarId") UUID dsarId,
        @Param("tenantId") UUID tenantId);
    
    Optional<DsarRequestEntity> findByTenantIdAndDataPrincipalIdAndIdempotencyKey(
        UUID tenantId, UUID dataPrincipalId, String idempotencyKey);
    
    Page<DsarRequestEntity> findByTenantId(UUID tenantId, Pageable pageable);
    
    Page<DsarRequestEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
    
    Page<DsarRequestEntity> findByTenantIdAndRequestType(UUID tenantId, String requestType, Pageable pageable);
    
    Page<DsarRequestEntity> findByTenantIdAndDataPrincipalId(UUID tenantId, UUID dataPrincipalId, Pageable pageable);
    
    @Query("SELECT d FROM DsarRequestEntity d WHERE d.dueAt < :now AND d.closedAt IS NULL AND d.slaBreached = false")
    List<DsarRequestEntity> findOverdueDsarRequests(@Param("now") Instant now);

    @Query("SELECT d FROM DsarRequestEntity d WHERE d.createdAt <= :cutoff AND d.status <> 'CLOSED'")
    List<DsarRequestEntity> findEscalationCandidates(@Param("cutoff") Instant cutoff);
}
