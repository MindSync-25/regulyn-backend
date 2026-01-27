package io.regulyn.identity.repository;

import io.regulyn.identity.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    
    Optional<Role> findByTenantIdAndRoleName(UUID tenantId, String roleName);
    
    List<Role> findByTenantId(UUID tenantId);
}
