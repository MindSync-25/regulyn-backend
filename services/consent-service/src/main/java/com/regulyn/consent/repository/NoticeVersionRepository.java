package com.regulyn.consent.repository;

import com.regulyn.consent.entity.NoticeVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoticeVersionRepository extends JpaRepository<NoticeVersion, UUID> {
    
    @Query("SELECT MAX(v.versionNumber) FROM NoticeVersion v WHERE v.tenantId = ?1 AND v.noticeId = ?2")
    Optional<Integer> findMaxVersionNumber(UUID tenantId, UUID noticeId);
    
    Optional<NoticeVersion> findByTenantIdAndNoticeIdAndStatus(UUID tenantId, UUID noticeId, String status);
    
    List<NoticeVersion> findByTenantIdAndNoticeIdAndStatusOrderByVersionNumberDesc(UUID tenantId, UUID noticeId, String status);
    
    Optional<NoticeVersion> findByTenantIdAndVersionId(UUID tenantId, UUID versionId);

    Optional<NoticeVersion> findTopByTenantIdAndNoticeIdOrderByVersionNumberDesc(UUID tenantId, UUID noticeId);
}
