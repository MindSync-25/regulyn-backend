package com.regulyn.ropa.repository;

import com.regulyn.ropa.model.RopaActivityDataCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RopaActivityDataCategoryRepository extends JpaRepository<RopaActivityDataCategory, UUID> {

    List<RopaActivityDataCategory> findByVersionId(UUID versionId);

    void deleteByVersionId(UUID versionId);
}
