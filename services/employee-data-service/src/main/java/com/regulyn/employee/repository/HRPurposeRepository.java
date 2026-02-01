package com.regulyn.employee.repository;

import com.regulyn.employee.model.HRPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HRPurposeRepository extends JpaRepository<HRPurpose, UUID> {

    Optional<HRPurpose> findByTenantIdAndHrPurposeId(UUID tenantId, UUID hrPurposeId);

    Optional<HRPurpose> findByTenantIdAndPurposeKey(UUID tenantId, HRPurpose.PurposeKey purposeKey);

    List<HRPurpose> findByTenantId(UUID tenantId);

    List<HRPurpose> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
