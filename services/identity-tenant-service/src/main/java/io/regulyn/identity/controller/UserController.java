package io.regulyn.identity.controller;

import io.regulyn.identity.dto.CreateUserRequest;
import io.regulyn.identity.dto.UserResponse;
import io.regulyn.identity.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<List<UserResponse>> listUsers(@RequestParam(value = "email", required = false) String email) {
        List<UserResponse> users = userService.listUsers(email);
        return ResponseEntity.ok(users);
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.ok(response);
    }
}
