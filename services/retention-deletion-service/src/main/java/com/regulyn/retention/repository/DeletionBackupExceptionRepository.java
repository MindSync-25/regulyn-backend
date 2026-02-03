package com.regulyn.retention.repository;

import com.regulyn.retention.entity.DeletionBackupException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeletionBackupExceptionRepository extends JpaRepository<DeletionBackupException, UUID> {
    
    List<DeletionBackupException> findByDeletionRequestId(UUID deletionRequestId);
    
    @Query("SELECT e FROM DeletionBackupException e WHERE e.retentionUntil <= :now")
    List<DeletionBackupException> findExpiredExceptions(Instant now);
}
