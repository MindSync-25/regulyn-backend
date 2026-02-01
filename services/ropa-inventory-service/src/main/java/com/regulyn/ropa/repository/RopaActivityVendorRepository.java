package com.regulyn.ropa.repository;

import com.regulyn.ropa.model.RopaActivityVendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RopaActivityVendorRepository extends JpaRepository<RopaActivityVendor, UUID> {

    List<RopaActivityVendor> findByVersionId(UUID versionId);

    void deleteByVersionId(UUID versionId);
}
