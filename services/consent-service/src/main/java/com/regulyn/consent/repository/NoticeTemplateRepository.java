package com.regulyn.consent.repository;

import com.regulyn.consent.entity.NoticeTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoticeTemplateRepository extends JpaRepository<NoticeTemplate, UUID> {
    
    Optional<NoticeTemplate> findByTenantIdAndPurpose(UUID tenantId, String purpose);
    
    boolean existsByTenantIdAndPurpose(UUID tenantId, String purpose);
}
