package io.regulyn.identity.repository;

import io.regulyn.identity.entity.UserRole;
import io.regulyn.identity.entity.UserRole.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    
    List<UserRole> findByUserIdAndTenantId(UUID userId, UUID tenantId);
    
    @Query("SELECT ur.roleId FROM UserRole ur WHERE ur.userId = :userId AND ur.tenantId = :tenantId")
    List<UUID> findRoleIdsByUserIdAndTenantId(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);
    
    void deleteByUserIdAndRoleId(UUID userId, UUID roleId);
}
