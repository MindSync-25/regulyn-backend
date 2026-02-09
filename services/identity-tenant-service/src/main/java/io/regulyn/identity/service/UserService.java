package io.regulyn.identity.service;

import com.regulyn.auth.context.TenantContextHolder;
import io.regulyn.identity.constants.TenantStatuses;
import io.regulyn.identity.dto.CreateUserRequest;
import io.regulyn.identity.dto.UserResponse;
import io.regulyn.identity.entity.Tenant;
import io.regulyn.identity.entity.User;
import io.regulyn.identity.repository.TenantRepository;
import io.regulyn.identity.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantWriteGuard tenantWriteGuard;

    public UserService(UserRepository userRepository,
                       TenantRepository tenantRepository,
                       PasswordEncoder passwordEncoder,
                       TenantWriteGuard tenantWriteGuard) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantWriteGuard = tenantWriteGuard;
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

        // Map to response
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId().toString());
        response.setTenantId(user.getTenantId().toString());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setEnabled(user.getEnabled());

        return response;
    }
}
