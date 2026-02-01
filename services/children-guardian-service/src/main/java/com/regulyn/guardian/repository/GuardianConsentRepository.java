package com.regulyn.guardian.repository;

import com.regulyn.guardian.entity.GuardianConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GuardianConsentRepository extends JpaRepository<GuardianConsent, UUID> {
    
    Optional<GuardianConsent> findByTenantIdAndConsentId(UUID tenantId, UUID consentId);
    
    List<GuardianConsent> findByTenantIdAndChildId(UUID tenantId, UUID childId);
    
    List<GuardianConsent> findByTenantIdAndGuardianId(UUID tenantId, UUID guardianId);
    
    @Query("SELECT c FROM GuardianConsent c WHERE c.tenantId = :tenantId " +
           "AND c.childId = :childId " +
           "AND c.guardianId = :guardianId " +
           "AND c.purposeKey = :purposeKey " +
           "AND c.idempotencyKey = :idempotencyKey")
    Optional<GuardianConsent> findByIdempotencyKey(
        @Param("tenantId") UUID tenantId,
        @Param("childId") UUID childId,
        @Param("guardianId") UUID guardianId,
        @Param("purposeKey") String purposeKey,
        @Param("idempotencyKey") String idempotencyKey
    );
    
    @Query("SELECT c FROM GuardianConsent c WHERE c.tenantId = :tenantId " +
           "AND c.status = :status")
    List<GuardianConsent> findByTenantIdAndStatus(
        @Param("tenantId") UUID tenantId,
        @Param("status") String status
    );
}

