package com.regulyn.guardian.api;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.guardian.dto.AgeEvaluationRequest;
import com.regulyn.guardian.dto.AgeEvaluationResponse;
import com.regulyn.guardian.dto.CreateChildRequest;
import com.regulyn.guardian.dto.CreateChildResponse;
import com.regulyn.guardian.entity.Child;
import com.regulyn.guardian.service.AgeRuleService;
import com.regulyn.guardian.service.ChildService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/children")
public class ChildController {
    
    private final ChildService childService;
    private final AgeRuleService ageRuleService;
    
    public ChildController(ChildService childService, AgeRuleService ageRuleService) {
        this.childService = childService;
        this.ageRuleService = ageRuleService;
    }
    
    @PostMapping
    public ResponseEntity<CreateChildResponse> createChild(@Valid @RequestBody CreateChildRequest request) {
        CreateChildResponse response = childService.createChild(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{childId}/age-evaluation")
    public ResponseEntity<AgeEvaluationResponse> evaluateAge(
            @PathVariable("childId") java.util.UUID childId,
            @Valid @RequestBody AgeEvaluationRequest request) {
        TenantContext context = TenantContextHolder.getContext();
        java.util.UUID tenantId = context.getTenantId();
        java.util.UUID actorId = context.getUserId();

        Child child = childService.getChild(childId);

        String countryCode = child.getRegionCountryCode();
        String stateCode = child.getRegionStateCode();
        if (countryCode == null || countryCode.isBlank()) {
            countryCode = request.countryCode();
            stateCode = request.stateCode();
        }

        if (countryCode == null || countryCode.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "REGION_REQUIRED"
            );
        }

        AgeRuleService.AgeEvaluationResult result = ageRuleService.evaluate(
                tenantId,
                child.getChildId(),
                child.getDateOfBirth(),
                countryCode,
                stateCode,
                request.evaluationDate()
        );

        AgeEvaluationResponse response = ageRuleService.publishAgeEvaluation(actorId, result);
        return ResponseEntity.ok(response);
    }
}
