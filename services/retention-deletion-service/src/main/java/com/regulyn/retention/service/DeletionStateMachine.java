package com.regulyn.retention.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * State machine for deletion workflow with strict transition rules.
 * States: REQUESTED -> IN_REVIEW -> APPROVED/REJECTED -> IN_PROGRESS -> COMPLETED/FAILED -> CLOSED
 */
@Component
public class DeletionStateMachine {

    private static final Map<String, List<String>> VALID_TRANSITIONS = Map.of(
            "REQUESTED", List.of("IN_REVIEW", "IN_PROGRESS", "CLOSED"),
            "IN_REVIEW", List.of("APPROVED", "REJECTED", "IN_PROGRESS", "CLOSED"),
            "APPROVED", List.of("IN_PROGRESS", "CLOSED"),
            "REJECTED", List.of("CLOSED"),
            "IN_PROGRESS", List.of("COMPLETED", "FAILED"),
            "COMPLETED", List.of("CLOSED"),
            "FAILED", List.of("IN_PROGRESS", "CLOSED")
    );

    private static final List<String> CLOSEABLE_STATUSES = List.of("COMPLETED", "FAILED", "REJECTED");

    public boolean isValidTransition(String fromStatus, String toStatus) {
        if (fromStatus == null || toStatus == null) {
            return false;
        }
        if ("CLOSED".equals(fromStatus)) {
            return false; // Cannot transition from CLOSED
        }
        List<String> allowedTargets = VALID_TRANSITIONS.get(fromStatus);
        return allowedTargets != null && allowedTargets.contains(toStatus);
    }

    public List<String> getAllowedTransitions(String fromStatus) {
        return VALID_TRANSITIONS.getOrDefault(fromStatus, List.of());
    }

    public boolean canClose(String currentStatus) {
        return CLOSEABLE_STATUSES.contains(currentStatus) || "REQUESTED".equals(currentStatus) || "IN_REVIEW".equals(currentStatus);
    }

    public boolean canApprove(String currentStatus) {
        return "IN_REVIEW".equals(currentStatus);
    }

    public boolean requiresApprovalCheck(String currentStatus, String targetStatus, boolean requiresApproval) {
        if ("IN_PROGRESS".equals(targetStatus)) {
            if (requiresApproval) {
                // Must be in APPROVED status to transition to IN_PROGRESS when requiresApproval=true
                return "APPROVED".equals(currentStatus);
            } else {
                // When approval not required, allow REQUESTED→IN_PROGRESS, IN_REVIEW→IN_PROGRESS, or APPROVED→IN_PROGRESS
                return "REQUESTED".equals(currentStatus) || "IN_REVIEW".equals(currentStatus) || "APPROVED".equals(currentStatus);
            }
        }
        return true;
    }
}
