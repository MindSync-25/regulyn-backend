package com.regulyn.consent.repository;

import com.regulyn.consent.entity.NoticeLanguageText;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoticeLanguageTextRepository extends JpaRepository<NoticeLanguageText, UUID> {
    
    Optional<NoticeLanguageText> findByTenantIdAndVersionIdAndLanguage(UUID tenantId, UUID versionId, String language);
    
    boolean existsByTenantIdAndVersionIdAndLanguage(UUID tenantId, UUID versionId, String language);
}
