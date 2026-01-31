package com.regulyn.nominee.service;

import com.regulyn.common.enums.NomineeClaimStatus;
import com.regulyn.common.workflow.WorkflowTransitionValidator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class NomineeClaimWorkflowValidator {

    private final WorkflowTransitionValidator<NomineeClaimStatus> validator;

    public NomineeClaimWorkflowValidator() {
        Map<NomineeClaimStatus, Set<NomineeClaimStatus>> allowedTransitions = Map.of(
            NomineeClaimStatus.CLAIM_SUBMITTED, Set.of(NomineeClaimStatus.UNDER_REVIEW, NomineeClaimStatus.REJECTED),
            NomineeClaimStatus.UNDER_REVIEW, Set.of(NomineeClaimStatus.APPROVED, NomineeClaimStatus.REJECTED),
            NomineeClaimStatus.APPROVED, Set.of(NomineeClaimStatus.CLOSED),
            NomineeClaimStatus.REJECTED, Set.of(NomineeClaimStatus.CLOSED)
        );
        this.validator = new WorkflowTransitionValidator<>("NomineeClaim", allowedTransitions);
    }

    public void validateTransition(NomineeClaimStatus currentStatus, NomineeClaimStatus nextStatus) {
        validator.validateTransition(currentStatus, nextStatus);
    }

    public boolean isTransitionAllowed(NomineeClaimStatus currentStatus, NomineeClaimStatus nextStatus) {
        return validator.isTransitionAllowed(currentStatus, nextStatus);
    }
}
