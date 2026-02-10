package com.regulyn.employee.persistence.repo;

import com.regulyn.employee.persistence.entity.DocType;
import com.regulyn.employee.persistence.entity.HRDocumentRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HrDocumentRuleRepository extends JpaRepository<HRDocumentRule, UUID> {
    Optional<HRDocumentRule> findByTenantIdAndDocType(UUID tenantId, DocType docType);
}
