package com.regulyn.consent.repository;

import com.regulyn.consent.entity.PurposeVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurposeVersionRepository extends JpaRepository<PurposeVersion, UUID> {
    Optional<PurposeVersion> findTopByTenantIdAndNoticeIdAndPurposeKeyOrderByVersionNumDesc(UUID tenantId,
                                                                                           UUID noticeId,
                                                                                           String purposeKey);

    Optional<PurposeVersion> findByTenantIdAndNoticeVersionIdAndPurposeKey(UUID tenantId,
                                                                          UUID noticeVersionId,
                                                                          String purposeKey);

    List<PurposeVersion> findByTenantIdOrderByPurposeKeyAscVersionNumDesc(UUID tenantId);
}
