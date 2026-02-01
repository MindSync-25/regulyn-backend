package com.regulyn.nominee.repository;

import com.regulyn.nominee.entity.ClaimStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClaimStatusHistoryRepository extends JpaRepository<ClaimStatusHistory, UUID> {

    List<ClaimStatusHistory> findByClaimIdOrderByTransitionedAtDesc(UUID claimId);
}
