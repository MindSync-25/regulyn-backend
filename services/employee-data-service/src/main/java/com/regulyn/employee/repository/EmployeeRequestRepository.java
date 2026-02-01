package com.regulyn.employee.repository;

import com.regulyn.employee.model.EmployeeRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRequestRepository extends JpaRepository<EmployeeRequest, UUID> {

    Optional<EmployeeRequest> findByTenantIdAndRequestId(UUID tenantId, UUID requestId);

    Optional<EmployeeRequest> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    Optional<EmployeeRequest> findByTenantIdAndEmployeeIdAndIdempotencyKey(
        UUID tenantId, UUID employeeId, String idempotencyKey);

    Page<EmployeeRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<EmployeeRequest> findByTenantIdAndStatusOrderByCreatedAtDesc(
        UUID tenantId, EmployeeRequest.RequestStatus status, Pageable pageable);

    Page<EmployeeRequest> findByTenantIdAndRequestTypeOrderByCreatedAtDesc(
        UUID tenantId, EmployeeRequest.RequestType requestType, Pageable pageable);

    Page<EmployeeRequest> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(
        UUID tenantId, UUID employeeId, Pageable pageable);

    Page<EmployeeRequest> findByTenantId(UUID tenantId, Pageable pageable);

    Page<EmployeeRequest> findByTenantIdAndStatus(
        UUID tenantId, EmployeeRequest.RequestStatus status, Pageable pageable);

    Page<EmployeeRequest> findByTenantIdAndRequestType(
        UUID tenantId, EmployeeRequest.RequestType requestType, Pageable pageable);

    Page<EmployeeRequest> findByTenantIdAndEmployeeId(
        UUID tenantId, UUID employeeId, Pageable pageable);

    Page<EmployeeRequest> findByTenantIdAndStatusAndRequestType(
        UUID tenantId, EmployeeRequest.RequestStatus status, 
        EmployeeRequest.RequestType requestType, Pageable pageable);

    Page<EmployeeRequest> findByTenantIdAndStatusAndEmployeeId(
        UUID tenantId, EmployeeRequest.RequestStatus status, 
        UUID employeeId, Pageable pageable);

    Page<EmployeeRequest> findByTenantIdAndRequestTypeAndEmployeeId(
        UUID tenantId, EmployeeRequest.RequestType requestType, 
        UUID employeeId, Pageable pageable);

    Page<EmployeeRequest> findByTenantIdAndStatusAndRequestTypeAndEmployeeId(
        UUID tenantId, EmployeeRequest.RequestStatus status, 
        EmployeeRequest.RequestType requestType, UUID employeeId, Pageable pageable);

    @Query("SELECT r FROM EmployeeRequest r WHERE r.status != 'CLOSED' AND r.slaBreached = false AND r.dueAt < :now")
    List<EmployeeRequest> findSLABreachedCandidates(@Param("now") Instant now);
}
