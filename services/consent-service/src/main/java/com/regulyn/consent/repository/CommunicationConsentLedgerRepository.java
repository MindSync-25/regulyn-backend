package com.regulyn.consent.repository;

import com.regulyn.consent.entity.CommunicationConsentLedger;
import com.regulyn.consent.entity.CommunicationChannel;
import com.regulyn.consent.entity.CommunicationConsentState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunicationConsentLedgerRepository extends JpaRepository<CommunicationConsentLedger, UUID> {
    Optional<CommunicationConsentLedger> findFirstByTenantIdAndDataPrincipalIdAndChannelOrderByEffectiveAtDesc(
            UUID tenantId,
            UUID dataPrincipalId,
            CommunicationChannel channel);

    boolean existsByTenantIdAndDataPrincipalIdAndChannelAndEffectiveTimeBucketAndState(
            UUID tenantId,
            UUID dataPrincipalId,
            CommunicationChannel channel,
            Instant effectiveTimeBucket,
            CommunicationConsentState state);

    Optional<CommunicationConsentLedger> findByTenantIdAndDataPrincipalIdAndChannelAndEffectiveTimeBucketAndState(
            UUID tenantId,
            UUID dataPrincipalId,
            CommunicationChannel channel,
            Instant effectiveTimeBucket,
            CommunicationConsentState state);

    @Query(value = """
            SELECT DISTINCT ON (data_principal_id)
                id AS ledgerId,
                data_principal_id AS dataPrincipalId,
                state AS state,
                effective_at AS effectiveAt,
                consent_text_hash_sha256 AS consentTextHashSha256
            FROM consent.communication_consent_ledger
            WHERE tenant_id = :tenantId
              AND channel = :channel
              AND data_principal_id IN (:principalIds)
                                                ORDER BY data_principal_id, effective_at DESC, created_at DESC, id DESC
            """, nativeQuery = true)
    List<LatestLedgerRow> findLatestByTenantAndChannelAndPrincipalIds(
            @Param("tenantId") UUID tenantId,
            @Param("channel") String channel,
            @Param("principalIds") List<UUID> principalIds);
}
