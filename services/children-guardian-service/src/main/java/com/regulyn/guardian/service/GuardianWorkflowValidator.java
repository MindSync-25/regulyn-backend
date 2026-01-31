package com.regulyn.guardian.service;

import com.regulyn.common.enums.GuardianVerificationStatus;
import com.regulyn.common.workflow.WorkflowTransitionValidator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class GuardianWorkflowValidator {

    private final WorkflowTransitionValidator<GuardianVerificationStatus> validator;

    public GuardianWorkflowValidator() {
        Map<GuardianVerificationStatus, Set<GuardianVerificationStatus>> allowedTransitions = Map.of(
            GuardianVerificationStatus.PENDING, Set.of(GuardianVerificationStatus.VERIFIED, GuardianVerificationStatus.REJECTED)
        );
        this.validator = new WorkflowTransitionValidator<>("GuardianVerification", allowedTransitions);
    }

    public void validateTransition(GuardianVerificationStatus currentStatus, GuardianVerificationStatus nextStatus) {
        validator.validateTransition(currentStatus, nextStatus);
    }

    public boolean isTransitionAllowed(GuardianVerificationStatus currentStatus, GuardianVerificationStatus nextStatus) {
        return validator.isTransitionAllowed(currentStatus, nextStatus);
    }
}
