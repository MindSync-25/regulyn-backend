package com.regulyn.retention.repository;

import com.regulyn.retention.entity.DeletionProof;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeletionProofRepository extends JpaRepository<DeletionProof, UUID> {

    List<DeletionProof> findByDeletionId(UUID deletionId);

    long countByDeletionId(UUID deletionId);
}
