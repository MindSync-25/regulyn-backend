package com.regulyn.notification.repository;

import com.regulyn.notification.entity.NotificationTemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationTemplateVersionRepository extends JpaRepository<NotificationTemplateVersion, UUID> {
    
    @Query("SELECT v FROM NotificationTemplateVersion v WHERE v.tenantId = :tenantId AND v.templateId = :templateId ORDER BY v.versionNumber DESC")
    List<NotificationTemplateVersion> findByTenantIdAndTemplateId(
        @Param("tenantId") String tenantId, 
        @Param("templateId") UUID templateId
    );
    
    @Query("SELECT v FROM NotificationTemplateVersion v WHERE v.tenantId = :tenantId AND v.templateId = :templateId AND v.status = 'PUBLISHED'")
    Optional<NotificationTemplateVersion> findPublishedVersion(
        @Param("tenantId") String tenantId, 
        @Param("templateId") UUID templateId
    );
    
    @Query("SELECT MAX(v.versionNumber) FROM NotificationTemplateVersion v WHERE v.tenantId = :tenantId AND v.templateId = :templateId")
    Optional<Integer> findMaxVersionNumber(
        @Param("tenantId") String tenantId, 
        @Param("templateId") UUID templateId
    );
    
    @Query("SELECT v FROM NotificationTemplateVersion v WHERE v.tenantId = :tenantId AND v.versionId = :versionId")
    Optional<NotificationTemplateVersion> findByTenantIdAndVersionId(
        @Param("tenantId") String tenantId, 
        @Param("versionId") UUID versionId
    );
    
    @Modifying
    @Query("UPDATE NotificationTemplateVersion v SET v.status = 'RETIRED', v.retiredAt = :retiredAt WHERE v.tenantId = :tenantId AND v.templateId = :templateId AND v.status = 'PUBLISHED'")
    int retirePublishedVersions(
        @Param("tenantId") String tenantId, 
        @Param("templateId") UUID templateId, 
        @Param("retiredAt") Instant retiredAt
    );
}
