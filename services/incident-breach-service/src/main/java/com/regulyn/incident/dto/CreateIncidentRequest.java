package com.regulyn.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

public record CreateIncidentRequest(
    @NotBlank
    @Pattern(regexp = "LOW|MED|HIGH|CRITICAL")
    String severity,
    
    String summary,
    
    Map<String, Object> metadata
) {
    public CreateIncidentRequest {
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
