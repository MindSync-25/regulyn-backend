package com.regulyn.ropa.repository;

import com.regulyn.ropa.model.RopaActivityLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RopaActivityLinkRepository extends JpaRepository<RopaActivityLink, UUID> {

    List<RopaActivityLink> findByTenantIdAndActivityId(UUID tenantId, UUID activityId);
}
