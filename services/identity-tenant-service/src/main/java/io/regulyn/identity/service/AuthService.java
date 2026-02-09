package io.regulyn.identity.service;

import com.regulyn.auth.jwt.JwtTokenService;
import io.regulyn.identity.dto.LoginRequest;
import io.regulyn.identity.dto.LoginResponse;
import io.regulyn.identity.entity.Tenant;
import io.regulyn.identity.entity.User;
import io.regulyn.identity.repository.TenantRepository;
import io.regulyn.identity.repository.RoleRepository;
import io.regulyn.identity.repository.UserRepository;
import io.regulyn.identity.repository.UserRoleRepository;
import io.regulyn.identity.constants.TenantStatuses;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(UserRepository userRepository,
                      UserRoleRepository userRoleRepository,
                      RoleRepository roleRepository,
                      TenantRepository tenantRepository,
                      PasswordEncoder passwordEncoder,
                      JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        // Find user by email across all tenants
        User user = userRepository.findAll().stream()
            .filter(u -> u.getEmail().equals(request.getEmail()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));

        // Verify password
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());

        if (!passwordMatches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        // Check if user is enabled
        if (!user.getEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "USER_DISABLED");
        }

        if (user.getLockedAt() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "USER_LOCKED");
        }

        // Get user roles
        List<UUID> roleIds = userRoleRepository.findRoleIdsByUserIdAndTenantId(
            user.getUserId(), user.getTenantId());
        
        List<String> roleNames = roleIds.stream()
            .map(roleId -> roleRepository.findById(roleId).orElse(null))
            .filter(role -> role != null)
            .map(role -> role.getRoleName())
            .collect(Collectors.toList());

        // Enforce tenant lifecycle status
        Tenant tenant = tenantRepository.findById(user.getTenantId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_NOT_FOUND"));

        if (TenantStatuses.SUSPENDED.equals(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_SUSPENDED");
        }
        if (TenantStatuses.DELETED.equals(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_DELETED");
        }
        if (TenantStatuses.DRAFT.equals(tenant.getStatus())) {
            boolean isAdmin = roleNames.contains("TENANT_ADMIN");
            boolean isBootstrapUser = tenant.getAdminBootstrapUserId() != null
                    && tenant.getAdminBootstrapUserId().equals(user.getUserId());
            if (!isAdmin || !isBootstrapUser) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_NOT_ACTIVE");
            }
        }

        // Generate JWT token
        String token = jwtTokenService.createToken(
            user.getUserId(),
            user.getTenantId(),
            roleNames
        );

        // Build response
        return new LoginResponse(
            token,
            user.getTenantId().toString(),
            user.getUserId().toString(),
            user.getEmail(),
            roleNames.stream().collect(Collectors.toSet())
        );
    }
}
