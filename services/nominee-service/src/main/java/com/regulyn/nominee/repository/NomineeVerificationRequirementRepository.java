package com.regulyn.nominee.repository;

import com.regulyn.nominee.entity.NomineeVerificationRequirement;
import com.regulyn.nominee.entity.NomineeVerificationRequirementId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NomineeVerificationRequirementRepository extends JpaRepository<NomineeVerificationRequirement, NomineeVerificationRequirementId> {
    List<NomineeVerificationRequirement> findByIdTenantIdAndActiveTrue(UUID tenantId);
}
