package io.regulyn.identity.controller;

import com.regulyn.auth.context.TenantContextHolder;
import io.regulyn.identity.dto.LoginRequest;
import io.regulyn.identity.dto.LoginResponse;
import io.regulyn.identity.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        var context = TenantContextHolder.getContext();
        return ResponseEntity.ok(Map.of(
            "tenantId", context.getTenantId(),
            "userId", context.getUserId(),
            "roles", context.getRoles()
        ));
    }
}
