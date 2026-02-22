package io.regulyn.identity.service;

import com.regulyn.auth.context.TenantContextHolder;
import io.regulyn.identity.constants.TenantStatuses;
import io.regulyn.identity.dto.CreateUserRequest;
import io.regulyn.identity.dto.UserResponse;
import io.regulyn.identity.entity.Role;
import io.regulyn.identity.entity.Tenant;
import io.regulyn.identity.entity.User;
import io.regulyn.identity.entity.UserRole;
import io.regulyn.identity.repository.RoleRepository;
import io.regulyn.identity.repository.TenantRepository;
import io.regulyn.identity.repository.UserRepository;
import io.regulyn.identity.repository.UserRoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantWriteGuard tenantWriteGuard;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    public UserService(UserRepository userRepository,
                       TenantRepository tenantRepository,
                       PasswordEncoder passwordEncoder,
                       TenantWriteGuard tenantWriteGuard,
                       UserRoleRepository userRoleRepository,
                       RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantWriteGuard = tenantWriteGuard;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        var context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();

        tenantWriteGuard.guardWrite(tenantId, "USER_CREATE");

        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_NOT_FOUND"));
        if (!TenantStatuses.ACTIVE.equals(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_NOT_ACTIVE");
        }

        // Check if user already exists
        if (userRepository.existsByTenantIdAndEmail(tenantId, request.getEmail())) {
            throw new RuntimeException("User with email already exists");
        }

        // Create user
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setCreatedBy(context.getUserId());

        user = userRepository.save(user);

        // Map to response (no roles yet on initial create)
        return toUserResponse(user, new ArrayList<>());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(String emailFilter) {
        var context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();

        List<User> users;
        if (emailFilter != null && !emailFilter.isBlank()) {
            // Search by email (case-insensitive partial match)
            users = userRepository.findByTenantId(tenantId).stream()
                .filter(u -> u.getEmail().toLowerCase().contains(emailFilter.toLowerCase()))
                .collect(Collectors.toList());
        } else {
            // List all users for tenant
            users = userRepository.findByTenantId(tenantId);
        }

        return users.stream()
            .map(u -> toUserResponse(u, fetchRoleNames(u.getUserId(), u.getTenantId())))
            .collect(Collectors.toList());
    }

    private List<String> fetchRoleNames(UUID userId, UUID tenantId) {
        List<UUID> roleIds = userRoleRepository.findRoleIdsByUserIdAndTenantId(userId, tenantId);
        return roleIds.stream()
            .map(rid -> roleRepository.findById(rid).map(Role::getRoleName).orElse(null))
            .filter(name -> name != null)
            .collect(Collectors.toList());
    }

    private UserResponse toUserResponse(User user, List<String> roles) {
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId().toString());
        response.setTenantId(user.getTenantId().toString());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setEnabled(user.getEnabled());
        response.setRoles(roles != null ? roles : new ArrayList<>());
        if (user.getLockedAt() != null) {
            response.setLockedAt(user.getLockedAt().toString());
        }
        return response;
    }

    @Transactional
    public UserResponse assignRoles(UUID userId, List<String> roleNames) {
        var context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();

        User user = userRepository.findById(userId)
            .filter(u -> u.getTenantId().equals(tenantId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        // Remove existing roles
        List<UserRole> existing = userRoleRepository.findByUserIdAndTenantId(userId, tenantId);
        for (UserRole ur : existing) {
            userRoleRepository.deleteByUserIdAndRoleId(ur.getUserId(), ur.getRoleId());
        }

        // Assign new roles
        List<String> assignedRoleNames = new ArrayList<>();
        for (String roleName : roleNames) {
            Role role = roleRepository.findByTenantIdAndRoleName(tenantId, roleName)
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setTenantId(tenantId);
                    newRole.setRoleName(roleName);
                    newRole.setCreatedBy(context.getUserId());
                    return roleRepository.save(newRole);
                });

            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(role.getRoleId());
            userRole.setTenantId(tenantId);
            userRole.setAssignedBy(context.getUserId());
            userRoleRepository.save(userRole);
            assignedRoleNames.add(roleName);
        }

        return toUserResponse(user, assignedRoleNames);
    }
}
