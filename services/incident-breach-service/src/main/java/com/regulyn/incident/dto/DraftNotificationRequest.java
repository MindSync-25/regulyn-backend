package com.regulyn.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

public record DraftNotificationRequest(
    @NotBlank
    @Pattern(regexp = "EMAIL|SMS|WHATSAPP")
    String channel,
    
    @NotBlank
    String draftText,
    
    Map<String, Object> metadata
) {
    public DraftNotificationRequest {
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
