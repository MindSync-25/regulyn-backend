package com.regulyn.dsar.service;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DsarStateMachine {
    
    // Define valid transitions
    private static final Map<String, List<String>> VALID_TRANSITIONS = new HashMap<>();
    
    static {
        VALID_TRANSITIONS.put("RECEIVED", Arrays.asList("IN_REVIEW"));
        VALID_TRANSITIONS.put("IN_REVIEW", Arrays.asList("NEEDS_INFO", "APPROVED", "REJECTED", "COMPLETED"));
        VALID_TRANSITIONS.put("NEEDS_INFO", Arrays.asList("IN_REVIEW"));
        VALID_TRANSITIONS.put("APPROVED", Arrays.asList("COMPLETED", "CLOSED"));
        VALID_TRANSITIONS.put("COMPLETED", Arrays.asList("CLOSED"));
        VALID_TRANSITIONS.put("REJECTED", Arrays.asList("CLOSED"));
        VALID_TRANSITIONS.put("CLOSED", Arrays.asList()); // No transitions from CLOSED
    }
    
    public boolean isValidTransition(String fromStatus, String toStatus) {
        List<String> allowedTransitions = VALID_TRANSITIONS.get(fromStatus);
        return allowedTransitions != null && allowedTransitions.contains(toStatus);
    }
    
    public List<String> getAllowedTransitions(String fromStatus) {
        return VALID_TRANSITIONS.getOrDefault(fromStatus, Arrays.asList());
    }
    
    public boolean canClose(String currentStatus) {
        return "COMPLETED".equals(currentStatus) || "APPROVED".equals(currentStatus) || "REJECTED".equals(currentStatus);
    }
}
