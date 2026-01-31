package com.regulyn.incident.service;

import com.regulyn.common.enums.IncidentStatus;
import com.regulyn.common.workflow.WorkflowTransitionValidator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class IncidentWorkflowValidator {

    private final WorkflowTransitionValidator<IncidentStatus> validator;

    public IncidentWorkflowValidator() {
        Map<IncidentStatus, Set<IncidentStatus>> allowedTransitions = Map.of(
            IncidentStatus.OPEN, Set.of(IncidentStatus.INVESTIGATING, IncidentStatus.CLOSED),
            IncidentStatus.INVESTIGATING, Set.of(IncidentStatus.CONTAINMENT, IncidentStatus.CLOSED),
            IncidentStatus.CONTAINMENT, Set.of(IncidentStatus.IMPACT_ASSESSED),
            IncidentStatus.IMPACT_ASSESSED, Set.of(IncidentStatus.NOTIFICATION_DRAFTED),
            IncidentStatus.NOTIFICATION_DRAFTED, Set.of(IncidentStatus.NOTIFICATION_APPROVED, IncidentStatus.INVESTIGATING),
            IncidentStatus.NOTIFICATION_APPROVED, Set.of(IncidentStatus.NOTIFIED),
            IncidentStatus.NOTIFIED, Set.of(IncidentStatus.REMEDIATION),
            IncidentStatus.REMEDIATION, Set.of(IncidentStatus.CLOSED)
        );
        this.validator = new WorkflowTransitionValidator<>("Incident", allowedTransitions);
    }

    public void validateTransition(IncidentStatus currentStatus, IncidentStatus nextStatus) {
        validator.validateTransition(currentStatus, nextStatus);
    }

    public boolean isTransitionAllowed(IncidentStatus currentStatus, IncidentStatus nextStatus) {
        return validator.isTransitionAllowed(currentStatus, nextStatus);
    }
}
