package com.regulyn.retention.repository;

import com.regulyn.retention.entity.DeletionTombstone;
import com.regulyn.retention.enums.DeletionTombstoneStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeletionTombstoneRepository extends JpaRepository<DeletionTombstone, UUID> {

    Optional<DeletionTombstone> findByTenantIdAndSubjectTypeAndSubjectRef(UUID tenantId, String subjectType, String subjectRef);

    List<DeletionTombstone> findByTenantIdAndTombstoneStatus(UUID tenantId, DeletionTombstoneStatus tombstoneStatus);
}
