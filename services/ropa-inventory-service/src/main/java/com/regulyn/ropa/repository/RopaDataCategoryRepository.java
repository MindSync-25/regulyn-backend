package com.regulyn.ropa.repository;

import com.regulyn.ropa.model.RopaDataCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RopaDataCategoryRepository extends JpaRepository<RopaDataCategory, UUID> {

    List<RopaDataCategory> findByTenantId(UUID tenantId);
}
