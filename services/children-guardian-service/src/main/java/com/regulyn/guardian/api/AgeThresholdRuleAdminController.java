package com.regulyn.guardian.api;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.guardian.dto.AgeRuleEffectiveResponse;
import com.regulyn.guardian.dto.AgeRuleResponse;
import com.regulyn.guardian.dto.AgeRuleUpsertRequest;
import com.regulyn.guardian.service.AgeRuleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/age-rules")
public class AgeThresholdRuleAdminController {

    private final AgeRuleService ageRuleService;

    public AgeThresholdRuleAdminController(AgeRuleService ageRuleService) {
        this.ageRuleService = ageRuleService;
    }

    @PutMapping
    public ResponseEntity<AgeRuleResponse> upsertRule(@Valid @RequestBody AgeRuleUpsertRequest request) {
        AgeRuleResponse response = ageRuleService.upsertRule(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<AgeRuleResponse>> listRules() {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        return ResponseEntity.ok(ageRuleService.listRules(tenantId));
    }

    @GetMapping("/effective")
    public ResponseEntity<AgeRuleEffectiveResponse> effectiveRule(
            @RequestParam("country") String country,
            @RequestParam(value = "state", required = false) String state) {
        TenantContext context = TenantContextHolder.getContext();
        UUID tenantId = context.getTenantId();
        return ResponseEntity.ok(ageRuleService.resolveEffectiveThreshold(tenantId, country, state));
    }
}
