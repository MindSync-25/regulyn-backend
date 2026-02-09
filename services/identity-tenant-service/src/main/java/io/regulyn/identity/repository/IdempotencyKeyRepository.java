package io.regulyn.identity.repository;

import io.regulyn.identity.entity.IdempotencyKeyEntity;
import io.regulyn.identity.entity.IdempotencyKeyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, IdempotencyKeyId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IdempotencyKeyEntity i where i.id.tenantId = :tenantId and i.id.scope = :scope and i.id.idempotencyKey = :idempotencyKey")
    Optional<IdempotencyKeyEntity> findForUpdate(@Param("tenantId") UUID tenantId,
                                                 @Param("scope") String scope,
                                                 @Param("idempotencyKey") String idempotencyKey);
}
