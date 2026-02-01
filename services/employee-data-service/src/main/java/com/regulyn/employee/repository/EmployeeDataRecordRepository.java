package com.regulyn.employee.repository;

import com.regulyn.employee.model.EmployeeDataRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeDataRecordRepository extends JpaRepository<EmployeeDataRecord, UUID> {

    Optional<EmployeeDataRecord> findByTenantIdAndRecordId(UUID tenantId, UUID recordId);

    List<EmployeeDataRecord> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<EmployeeDataRecord> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    List<EmployeeDataRecord> findByTenantIdAndHrPurposeId(UUID tenantId, UUID hrPurposeId);

    List<EmployeeDataRecord> findByTenantIdAndDataCategory(UUID tenantId, EmployeeDataRecord.DataCategory dataCategory);

    List<EmployeeDataRecord> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(UUID tenantId, UUID employeeId);

    List<EmployeeDataRecord> findByTenantIdAndDataCategoryOrderByCreatedAtDesc(UUID tenantId, EmployeeDataRecord.DataCategory dataCategory);

    List<EmployeeDataRecord> findByTenantIdAndHrPurposeIdOrderByCreatedAtDesc(UUID tenantId, UUID hrPurposeId);

    List<EmployeeDataRecord> findByTenantIdAndSystemIdOrderByCreatedAtDesc(UUID tenantId, UUID systemId);
}
