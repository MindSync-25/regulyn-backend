package com.regulyn.dsar.service;

import com.regulyn.common.enums.DSARStatus;
import com.regulyn.common.workflow.WorkflowTransitionValidator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class DsarWorkflowValidator {
    
    private final WorkflowTransitionValidator<DSARStatus> validator;
    
    public DsarWorkflowValidator() {
        // Define allowed transitions
        Map<DSARStatus, Set<DSARStatus>> transitions = Map.of(
            DSARStatus.RECEIVED, Set.of(DSARStatus.ASSIGNED, DSARStatus.IN_PROGRESS, DSARStatus.REJECTED),
            DSARStatus.ASSIGNED, Set.of(DSARStatus.IN_PROGRESS, DSARStatus.REJECTED),
            DSARStatus.IN_PROGRESS, Set.of(DSARStatus.PENDING_APPROVAL, DSARStatus.COMPLETED, DSARStatus.REJECTED),
            DSARStatus.PENDING_APPROVAL, Set.of(DSARStatus.APPROVED, DSARStatus.REJECTED, DSARStatus.IN_PROGRESS),
            DSARStatus.APPROVED, Set.of(DSARStatus.COMPLETED),
            DSARStatus.REJECTED, Set.of(DSARStatus.CLOSED),
            DSARStatus.COMPLETED, Set.of(DSARStatus.CLOSED)
        );
        
        this.validator = new WorkflowTransitionValidator<>("DSAR", transitions);
    }
    
    public void validateTransition(DSARStatus from, DSARStatus to) {
        validator.validateTransition(from, to);
    }
    
    public boolean isTransitionAllowed(DSARStatus from, DSARStatus to) {
        return validator.isTransitionAllowed(from, to);
    }
}
