package com.regulyn.incident.repository;

import com.regulyn.incident.entity.NoticeTemplateVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NoticeTemplateVersionRepository extends JpaRepository<NoticeTemplateVersionEntity, UUID> {
    boolean existsByTemplateIdAndVersionAndLanguage(UUID templateId, Integer version, String language);

    Optional<NoticeTemplateVersionEntity> findByTemplateIdAndVersionAndLanguage(UUID templateId, Integer version, String language);

    @Query("select max(v.version) from NoticeTemplateVersionEntity v where v.templateId = :templateId")
    Integer findMaxVersionForTemplate(@Param("templateId") UUID templateId);
}
