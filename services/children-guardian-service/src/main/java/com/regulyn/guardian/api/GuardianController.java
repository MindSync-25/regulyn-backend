package com.regulyn.guardian.api;

import com.regulyn.guardian.dto.CreateGuardianRequest;
import com.regulyn.guardian.dto.CreateGuardianResponse;
import com.regulyn.guardian.dto.VerifyGuardianRequest;
import com.regulyn.guardian.dto.VerifyGuardianResponse;
import com.regulyn.guardian.service.GuardianService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/guardians")
public class GuardianController {
    
    private final GuardianService guardianService;
    
    public GuardianController(GuardianService guardianService) {
        this.guardianService = guardianService;
    }
    
    @PostMapping
    public ResponseEntity<CreateGuardianResponse> createGuardian(@Valid @RequestBody CreateGuardianRequest request) {
        CreateGuardianResponse response = guardianService.createGuardian(request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{guardianId}/verify")
    public ResponseEntity<VerifyGuardianResponse> verifyGuardian(
            @PathVariable("guardianId") UUID guardianId,
            @Valid @RequestBody VerifyGuardianRequest request) {
        VerifyGuardianResponse response = guardianService.verifyGuardian(guardianId, request);
        return ResponseEntity.ok(response);
    }
}
