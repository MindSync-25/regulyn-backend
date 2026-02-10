package com.regulyn.ropa.repository;

import com.regulyn.ropa.model.RetentionPolicySystemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetentionPolicySystemRepository extends JpaRepository<RetentionPolicySystemEntity, UUID> {

    Optional<RetentionPolicySystemEntity> findByTenantIdAndSystemId(UUID tenantId, UUID systemId);

    Optional<RetentionPolicySystemEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
