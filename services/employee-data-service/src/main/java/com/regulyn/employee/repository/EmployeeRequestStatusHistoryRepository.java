package com.regulyn.employee.repository;

import com.regulyn.employee.model.EmployeeRequestStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmployeeRequestStatusHistoryRepository extends JpaRepository<EmployeeRequestStatusHistory, UUID> {

    List<EmployeeRequestStatusHistory> findByTenantIdAndRequestIdOrderByChangedAtAsc(UUID tenantId, UUID requestId);
}
