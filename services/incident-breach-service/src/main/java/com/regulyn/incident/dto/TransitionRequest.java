package com.regulyn.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TransitionRequest(
    @NotBlank
    @Pattern(regexp = "TRIAGED|INVESTIGATING|NOTIFIED|CONTAINED|CLOSED")
    String toStatus,
    
    String reason
) {}
