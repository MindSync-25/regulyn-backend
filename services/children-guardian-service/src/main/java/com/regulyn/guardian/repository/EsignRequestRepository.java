package com.regulyn.guardian.repository;

import com.regulyn.guardian.entity.EsignRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EsignRequestRepository extends JpaRepository<EsignRequestEntity, UUID> {

    Optional<EsignRequestEntity> findByTenantIdAndChildIdAndGuardianIdAndDocTypeAndDocVersion(
            UUID tenantId,
            UUID childId,
            UUID guardianId,
            String docType,
            Integer docVersion
    );

    Optional<EsignRequestEntity> findByTenantIdAndProviderAndProviderEnvelopeId(
            UUID tenantId,
            String provider,
            String providerEnvelopeId
    );

    Optional<EsignRequestEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    boolean existsByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
