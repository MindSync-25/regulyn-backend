package com.regulyn.common.workflow;

/**
 * Exception thrown when an invalid workflow state transition is attempted.
 */
public class InvalidWorkflowTransitionException extends RuntimeException {
    
    private final String workflowName;
    private final String fromState;
    private final String toState;
    
    public InvalidWorkflowTransitionException(String workflowName, String fromState, String toState, String message) {
        super(String.format("[%s] %s (from: %s, to: %s)", workflowName, message, fromState, toState));
        this.workflowName = workflowName;
        this.fromState = fromState;
        this.toState = toState;
    }
    
    public String getWorkflowName() {
        return workflowName;
    }
    
    public String getFromState() {
        return fromState;
    }
    
    public String getToState() {
        return toState;
    }
}
