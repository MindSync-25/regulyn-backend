package io.regulyn.identity.repository;

import io.regulyn.identity.entity.UserInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface UserInviteRepository extends JpaRepository<UserInvite, UUID> {

    @Query("select i from UserInvite i where i.tenantId = :tenantId and lower(i.email) = lower(:email) and i.usedAt is null")
    Optional<UserInvite> findActiveByTenantIdAndEmail(@Param("tenantId") UUID tenantId,
                                                      @Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from UserInvite i where i.tenantId = :tenantId and lower(i.email) = lower(:email) and i.usedAt is null")
    Optional<UserInvite> findActiveByTenantIdAndEmailForUpdate(@Param("tenantId") UUID tenantId,
                                                               @Param("email") String email);

    Optional<UserInvite> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from UserInvite i where i.tokenHash = :tokenHash")
    Optional<UserInvite> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
