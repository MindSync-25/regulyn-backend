package com.regulyn.guardian.service;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ConsentStateMachine {
    
    // Consent statuses
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_IN_REVIEW = "IN_REVIEW";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String STATUS_CLOSED = "CLOSED";
    
    // Guardian statuses
    public static final String GUARDIAN_PENDING = "PENDING";
    public static final String GUARDIAN_VERIFIED = "VERIFIED";
    public static final String GUARDIAN_DISABLED = "DISABLED";
    
    /**
     * Valid state transitions:
     * SUBMITTED -> IN_REVIEW (optional review step)
     * SUBMITTED -> APPROVED (direct approval)
     * SUBMITTED -> REJECTED (direct rejection)
     * IN_REVIEW -> APPROVED
     * IN_REVIEW -> REJECTED
     * APPROVED -> REVOKED
     * APPROVED -> CLOSED
     * REJECTED -> CLOSED
     * REVOKED -> CLOSED
     * CLOSED -> terminal (no further transitions)
     */
    public boolean canTransition(String currentStatus, String newStatus) {
        if (currentStatus == null || newStatus == null) {
            return false;
        }
        
        if (currentStatus.equals(newStatus)) {
            return false; // No self-transitions
        }
        
        return switch (currentStatus) {
            case STATUS_SUBMITTED -> 
                newStatus.equals(STATUS_IN_REVIEW) || 
                newStatus.equals(STATUS_APPROVED) || 
                newStatus.equals(STATUS_REJECTED);
                
            case STATUS_IN_REVIEW -> 
                newStatus.equals(STATUS_APPROVED) || 
                newStatus.equals(STATUS_REJECTED);
                
            case STATUS_APPROVED -> 
                newStatus.equals(STATUS_REVOKED) || 
                newStatus.equals(STATUS_CLOSED);
                
            case STATUS_REJECTED -> 
                newStatus.equals(STATUS_CLOSED);
                
            case STATUS_REVOKED -> 
                newStatus.equals(STATUS_CLOSED);
                
            case STATUS_CLOSED -> 
                false; // Terminal state
                
            default -> false;
        };
    }
    
    public boolean isTerminalStatus(String status) {
        return STATUS_CLOSED.equals(status);
    }
    
    public boolean canRevoke(String currentStatus) {
        return STATUS_APPROVED.equals(currentStatus);
    }
    
    public boolean requiresApproval(String currentStatus) {
        return STATUS_SUBMITTED.equals(currentStatus) || STATUS_IN_REVIEW.equals(currentStatus);
    }
    
    public Set<String> getAllValidStatuses() {
        return Set.of(
            STATUS_SUBMITTED,
            STATUS_IN_REVIEW,
            STATUS_APPROVED,
            STATUS_REJECTED,
            STATUS_REVOKED,
            STATUS_CLOSED
        );
    }
    
    public boolean isGuardianEligible(String guardianStatus) {
        return GUARDIAN_VERIFIED.equals(guardianStatus);
    }
}
