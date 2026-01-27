package io.regulyn.identity.repository;

import io.regulyn.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);
    
    List<User> findByTenantId(UUID tenantId);
    
    boolean existsByTenantIdAndEmail(UUID tenantId, String email);
}
