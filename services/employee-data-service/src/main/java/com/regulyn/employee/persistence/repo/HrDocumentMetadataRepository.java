package com.regulyn.employee.persistence.repo;

import com.regulyn.employee.persistence.entity.HRDocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HrDocumentMetadataRepository extends JpaRepository<HRDocumentMetadata, UUID> {
    Optional<HRDocumentMetadata> findByTenantIdAndDocId(UUID tenantId, UUID docId);
    boolean existsByTenantIdAndEmployeeIdAndIdempotencyKey(UUID tenantId, UUID employeeId, String idempotencyKey);
}
