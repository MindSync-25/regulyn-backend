package com.regulyn.guardian.repository;

import com.regulyn.guardian.entity.EsignWebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EsignWebhookEventRepository extends JpaRepository<EsignWebhookEventEntity, UUID> {

    boolean existsByTenantIdAndProviderAndProviderEnvelopeIdAndProviderEventId(
            UUID tenantId,
            String provider,
            String providerEnvelopeId,
            String providerEventId
    );
}
