package com.regulyn.notification.repository;

import com.regulyn.notification.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    
    @Query("SELECT t FROM NotificationTemplate t WHERE t.tenantId = :tenantId AND t.templateKey = :templateKey")
    Optional<NotificationTemplate> findByTenantIdAndTemplateKey(
        @Param("tenantId") String tenantId, 
        @Param("templateKey") String templateKey
    );
    
    @Query("SELECT t FROM NotificationTemplate t WHERE t.tenantId = :tenantId AND t.templateId = :templateId")
    Optional<NotificationTemplate> findByTenantIdAndTemplateId(
        @Param("tenantId") String tenantId, 
        @Param("templateId") UUID templateId
    );
    
    @Query("SELECT t FROM NotificationTemplate t WHERE t.tenantId = :tenantId AND t.templateKey = :templateKey AND t.enabled = true")
    Optional<NotificationTemplate> findEnabledByTenantIdAndTemplateKey(
        @Param("tenantId") String tenantId, 
        @Param("templateKey") String templateKey
    );
}
