package com.regulyn.ropa.repository;

import com.regulyn.ropa.model.RetentionPolicyActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetentionPolicyActivityRepository extends JpaRepository<RetentionPolicyActivityEntity, UUID> {

    Optional<RetentionPolicyActivityEntity> findByTenantIdAndActivityId(UUID tenantId, UUID activityId);

    Optional<RetentionPolicyActivityEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
