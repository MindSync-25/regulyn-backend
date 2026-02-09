package io.regulyn.identity.controller;

import io.regulyn.identity.dto.AcceptInviteRequest;
import io.regulyn.identity.dto.AcceptInviteResponse;
import io.regulyn.identity.dto.CreateInviteRequest;
import io.regulyn.identity.dto.InviteResponse;
import io.regulyn.identity.service.UserInviteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/invites")
public class UserInviteController {

    private final UserInviteService userInviteService;

    public UserInviteController(UserInviteService userInviteService) {
        this.userInviteService = userInviteService;
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<InviteResponse> createInvite(@RequestBody CreateInviteRequest request,
                                                       @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(userInviteService.createInvite(request, idempotencyKey));
    }

    @PostMapping("/accept")
    public ResponseEntity<AcceptInviteResponse> acceptInvite(@RequestBody AcceptInviteRequest request) {
        return ResponseEntity.ok(userInviteService.acceptInvite(request));
    }
}
