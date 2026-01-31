package com.regulyn.retention.repository;

import com.regulyn.retention.entity.RetentionCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetentionCandidateRepository extends JpaRepository<RetentionCandidate, UUID> {

    Optional<RetentionCandidate> findByTenantIdAndSubjectIdAndEntityType(UUID tenantId, UUID subjectId, String entityType);

    @Query("SELECT c FROM RetentionCandidate c WHERE c.tenantId = :tenantId AND c.subjectType = :subjectType AND c.entityType = :entityType AND c.lastSeenAt < :threshold")
    List<RetentionCandidate> findEligibleForRule(@Param("tenantId") UUID tenantId, @Param("subjectType") String subjectType, @Param("entityType") String entityType, @Param("threshold") Instant threshold);
}
