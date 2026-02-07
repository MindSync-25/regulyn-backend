package com.regulyn.retention.repository;

import com.regulyn.retention.entity.DeletionBackupException;
import com.regulyn.retention.enums.DeletionBackupExceptionStatus;
import com.regulyn.retention.enums.DeletionBackupExceptionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeletionBackupExceptionRepository extends JpaRepository<DeletionBackupException, UUID> {

    List<DeletionBackupException> findByTenantIdAndDeletionId(UUID tenantId, UUID deletionId);

    List<DeletionBackupException> findByTenantIdAndStatusAndNotBeforeBefore(UUID tenantId, DeletionBackupExceptionStatus status, Instant notBefore);

    Optional<DeletionBackupException> findByTenantIdAndExecutionIdAndStatus(UUID tenantId, UUID executionId, DeletionBackupExceptionStatus status);
}
