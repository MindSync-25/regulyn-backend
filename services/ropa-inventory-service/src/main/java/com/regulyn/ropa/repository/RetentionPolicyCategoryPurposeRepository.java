package com.regulyn.ropa.repository;

import com.regulyn.ropa.model.RetentionPolicyCategoryPurposeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetentionPolicyCategoryPurposeRepository extends JpaRepository<RetentionPolicyCategoryPurposeEntity, UUID> {

    Optional<RetentionPolicyCategoryPurposeEntity> findByTenantIdAndDataCategoryIdAndPurposeVersionId(
            UUID tenantId,
            UUID dataCategoryId,
            UUID purposeVersionId
    );

    Optional<RetentionPolicyCategoryPurposeEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
