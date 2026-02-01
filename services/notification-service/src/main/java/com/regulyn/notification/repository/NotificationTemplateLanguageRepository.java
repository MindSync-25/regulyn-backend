package com.regulyn.notification.repository;

import com.regulyn.notification.entity.NotificationTemplateLanguage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationTemplateLanguageRepository extends JpaRepository<NotificationTemplateLanguage, UUID> {
    
    @Query("SELECT l FROM NotificationTemplateLanguage l WHERE l.tenantId = :tenantId AND l.versionId = :versionId")
    List<NotificationTemplateLanguage> findByTenantIdAndVersionId(
        @Param("tenantId") String tenantId, 
        @Param("versionId") UUID versionId
    );
    
    @Query("SELECT l FROM NotificationTemplateLanguage l WHERE l.tenantId = :tenantId AND l.versionId = :versionId AND l.language = :language")
    Optional<NotificationTemplateLanguage> findByTenantIdAndVersionIdAndLanguage(
        @Param("tenantId") String tenantId, 
        @Param("versionId") UUID versionId, 
        @Param("language") String language
    );
}
