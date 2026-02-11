package com.regulyn.guardian.repository;

import com.regulyn.guardian.entity.ChildMajorityTransitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChildMajorityTransitionRepository extends JpaRepository<ChildMajorityTransitionEntity, UUID> {

    boolean existsByTenantIdAndChildIdAndMajorityDate(UUID tenantId, UUID childId, LocalDate majorityDate);

        java.util.Optional<ChildMajorityTransitionEntity> findByTenantIdAndChildIdAndMajorityDate(
            UUID tenantId,
            UUID childId,
            LocalDate majorityDate
        );

    List<ChildMajorityTransitionEntity> findByTenantIdAndMajorityDateLessThanEqualAndTransitionStatusIn(
            UUID tenantId,
            LocalDate majorityDate,
            List<String> transitionStatus
    );
}
