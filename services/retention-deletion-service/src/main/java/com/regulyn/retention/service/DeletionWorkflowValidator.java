package com.regulyn.retention.service;

import com.regulyn.common.enums.DeletionStatus;
import com.regulyn.common.workflow.WorkflowTransitionValidator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class DeletionWorkflowValidator {

    private final WorkflowTransitionValidator<DeletionStatus> validator;

    public DeletionWorkflowValidator() {
        Map<DeletionStatus, Set<DeletionStatus>> allowedTransitions = Map.of(
            DeletionStatus.PENDING, Set.of(DeletionStatus.ASSIGNED, DeletionStatus.REJECTED, DeletionStatus.CLOSED),
            DeletionStatus.ASSIGNED, Set.of(DeletionStatus.IN_PROGRESS, DeletionStatus.REJECTED),
            DeletionStatus.IN_PROGRESS, Set.of(DeletionStatus.AWAITING_PROOF, DeletionStatus.PENDING_APPROVAL, DeletionStatus.REJECTED),
            DeletionStatus.AWAITING_PROOF, Set.of(DeletionStatus.PENDING_APPROVAL, DeletionStatus.REJECTED),
            DeletionStatus.PENDING_APPROVAL, Set.of(DeletionStatus.APPROVED, DeletionStatus.REJECTED),
            DeletionStatus.APPROVED, Set.of(DeletionStatus.COMPLETED),
            DeletionStatus.REJECTED, Set.of(DeletionStatus.CLOSED),
            DeletionStatus.COMPLETED, Set.of(DeletionStatus.CLOSED)
        );
        this.validator = new WorkflowTransitionValidator<>("Deletion", allowedTransitions);
    }

    public void validateTransition(DeletionStatus currentStatus, DeletionStatus nextStatus) {
        validator.validateTransition(currentStatus, nextStatus);
    }

    public boolean isTransitionAllowed(DeletionStatus currentStatus, DeletionStatus nextStatus) {
        return validator.isTransitionAllowed(currentStatus, nextStatus);
    }
}
