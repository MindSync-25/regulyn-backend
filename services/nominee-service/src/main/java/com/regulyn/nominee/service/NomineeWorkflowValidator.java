package com.regulyn.nominee.service;

import com.regulyn.common.enums.NomineeStatus;
import com.regulyn.common.workflow.WorkflowTransitionValidator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class NomineeWorkflowValidator {

    private final WorkflowTransitionValidator<NomineeStatus> validator;

    public NomineeWorkflowValidator() {
        // Simplified - no workflow validation for now
        Map<NomineeStatus, Set<NomineeStatus>> allowedTransitions = Map.of();
        this.validator = new WorkflowTransitionValidator<>("Nominee", allowedTransitions);
    }

    public void validateTransition(NomineeStatus currentStatus, NomineeStatus nextStatus) {
        validator.validateTransition(currentStatus, nextStatus);
    }

    public boolean isTransitionAllowed(NomineeStatus currentStatus, NomineeStatus nextStatus) {
        return validator.isTransitionAllowed(currentStatus, nextStatus);
    }
}
