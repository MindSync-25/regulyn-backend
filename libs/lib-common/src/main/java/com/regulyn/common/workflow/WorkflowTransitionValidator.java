package com.regulyn.common.workflow;

import java.util.Map;
import java.util.Set;

/**
 * Generic workflow transition validator.
 * Validates state transitions based on configured allowed transitions.
 */
public class WorkflowTransitionValidator<T extends Enum<T>> {
    
    private final Map<T, Set<T>> allowedTransitions;
    private final String workflowName;
    
    public WorkflowTransitionValidator(String workflowName, Map<T, Set<T>> allowedTransitions) {
        this.workflowName = workflowName;
        this.allowedTransitions = allowedTransitions;
    }
    
    /**
     * Validates if a transition from current state to next state is allowed.
     * 
     * @param currentState The current state
     * @param nextState The desired next state
     * @throws InvalidWorkflowTransitionException if transition is not allowed
     */
    public void validateTransition(T currentState, T nextState) {
        if (currentState == null) {
            throw new InvalidWorkflowTransitionException(
                workflowName,
                null,
                nextState != null ? nextState.name() : null,
                "Current state cannot be null"
            );
        }
        
        if (nextState == null) {
            throw new InvalidWorkflowTransitionException(
                workflowName,
                currentState.name(),
                null,
                "Next state cannot be null"
            );
        }
        
        Set<T> allowedNextStates = allowedTransitions.get(currentState);
        
        if (allowedNextStates == null || !allowedNextStates.contains(nextState)) {
            throw new InvalidWorkflowTransitionException(
                workflowName,
                currentState.name(),
                nextState.name(),
                String.format("Transition from %s to %s is not allowed", currentState, nextState)
            );
        }
    }
    
    /**
     * Checks if a transition is allowed without throwing exception.
     */
    public boolean isTransitionAllowed(T currentState, T nextState) {
        if (currentState == null || nextState == null) {
            return false;
        }
        
        Set<T> allowedNextStates = allowedTransitions.get(currentState);
        return allowedNextStates != null && allowedNextStates.contains(nextState);
    }
}
