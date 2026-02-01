package com.regulyn.employee.repository;

import com.regulyn.employee.model.EmployeeExport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeExportRepository extends JpaRepository<EmployeeExport, UUID> {

    Optional<EmployeeExport> findByTenantIdAndExportId(UUID tenantId, UUID exportId);
}
