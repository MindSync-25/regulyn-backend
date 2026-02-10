package com.regulyn.employee.persistence.repo;

import com.regulyn.employee.persistence.entity.EmployeeAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmployeeAccessLogRepository extends JpaRepository<EmployeeAccessLog, UUID> {
    List<EmployeeAccessLog> findByTenantIdAndEmployeeIdOrderByOccurredAtDesc(UUID tenantId, UUID employeeId);
    List<EmployeeAccessLog> findByTenantIdAndUserIdOrderByOccurredAtDesc(UUID tenantId, UUID userId);
}
