package com.regulyn.employee.persistence.repo;

import com.regulyn.employee.persistence.entity.EmployeeAccessLogExport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeAccessLogExportRepository extends JpaRepository<EmployeeAccessLogExport, UUID> {
    Optional<EmployeeAccessLogExport> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
