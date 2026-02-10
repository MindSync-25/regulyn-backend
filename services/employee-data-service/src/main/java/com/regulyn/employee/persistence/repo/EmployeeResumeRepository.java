package com.regulyn.employee.persistence.repo;

import com.regulyn.employee.persistence.entity.EmployeeResume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface EmployeeResumeRepository extends JpaRepository<EmployeeResume, UUID> {
    Optional<EmployeeResume> findByTenantIdAndResumeId(UUID tenantId, UUID resumeId);
    boolean existsByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
    List<EmployeeResume> findByStatusInAndDeleteAfterLessThanEqualOrderByDeleteAfterAsc(
            List<String> statuses,
            OffsetDateTime deleteAfter,
            Pageable pageable
    );
}
