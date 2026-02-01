package com.regulyn.ropa.repository;

import com.regulyn.ropa.model.RopaActivitySystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RopaActivitySystemRepository extends JpaRepository<RopaActivitySystem, UUID> {

    List<RopaActivitySystem> findByVersionId(UUID versionId);

    void deleteByVersionId(UUID versionId);
}
