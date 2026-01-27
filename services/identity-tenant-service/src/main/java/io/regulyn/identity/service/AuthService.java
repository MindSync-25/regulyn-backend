package io.regulyn.identity.service;

import com.regulyn.auth.jwt.JwtTokenService;
import io.regulyn.identity.dto.LoginRequest;
import io.regulyn.identity.dto.LoginResponse;
import io.regulyn.identity.entity.User;
import io.regulyn.identity.entity.UserRole;
import io.regulyn.identity.repository.RoleRepository;
import io.regulyn.identity.repository.UserRepository;
import io.regulyn.identity.repository.UserRoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(UserRepository userRepository,
                      UserRoleRepository userRoleRepository,
                      RoleRepository roleRepository,
                      PasswordEncoder passwordEncoder,
                      JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        // Find user by email across all tenants
        User user = userRepository.findAll().stream()
            .filter(u -> u.getEmail().equals(request.getEmail()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        // Check if user is enabled
        if (!user.getEnabled()) {
            throw new RuntimeException("User account is disabled");
        }

        // Get user roles
        List<UUID> roleIds = userRoleRepository.findRoleIdsByUserIdAndTenantId(
            user.getUserId(), user.getTenantId());
        
        List<String> roleNames = roleIds.stream()
            .map(roleId -> roleRepository.findById(roleId).orElse(null))
            .filter(role -> role != null)
            .map(role -> role.getRoleName())
            .collect(Collectors.toList());

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
