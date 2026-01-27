package io.regulyn.identity.service;

import com.regulyn.auth.context.TenantContextHolder;
import io.regulyn.identity.dto.CreateUserRequest;
import io.regulyn.identity.dto.UserResponse;
import io.regulyn.identity.entity.User;
import io.regulyn.identity.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        var context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();

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
