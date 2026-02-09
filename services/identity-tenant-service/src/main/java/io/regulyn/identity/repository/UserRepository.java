package io.regulyn.identity.repository;

import io.regulyn.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);
    
    List<User> findByTenantId(UUID tenantId);
    
    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    long countByTenantIdAndEnabledTrue(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") UUID userId);
}
