package com.regulyn.retention.repository;

import com.regulyn.retention.entity.RetentionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetentionRuleRepository extends JpaRepository<RetentionRule, UUID> {

    Optional<RetentionRule> findByTenantIdAndRuleName(UUID tenantId, String ruleName);

    List<RetentionRule> findByTenantIdAndEnabled(UUID tenantId, Boolean enabled);

    @Query("SELECT r FROM RetentionRule r WHERE r.tenantId = :tenantId AND r.subjectType = :subjectType AND r.entityType = :entityType AND r.enabled = true")
    List<RetentionRule> findActiveRulesBySubjectAndEntity(UUID tenantId, String subjectType, String entityType);
}
