package io.regulyn.identity.controller;

import io.regulyn.identity.dto.LockUserRequest;
import io.regulyn.identity.dto.LockUserResponse;
import io.regulyn.identity.dto.UnlockUserRequest;
import io.regulyn.identity.dto.UnlockUserResponse;
import io.regulyn.identity.service.UserAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @PostMapping("/{userId}/lock")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<LockUserResponse> lockUser(@PathVariable("userId") UUID userId,
                                                     @RequestBody(required = false) LockUserRequest request) {
        LockUserResponse response = userAdminService.lockUser(userId, request != null ? request.getReason() : null);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/unlock")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<UnlockUserResponse> unlockUser(@PathVariable("userId") UUID userId,
                                                         @RequestBody(required = false) UnlockUserRequest request) {
        UnlockUserResponse response = userAdminService.unlockUser(userId, request != null ? request.getReason() : null);
        return ResponseEntity.ok(response);
    }
}
