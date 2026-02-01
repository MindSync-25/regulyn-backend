package com.regulyn.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record SendNotificationRequest(
    String requestRef,
    
    @NotBlank
    @Pattern(regexp = "BREACH_NOTICE|DSAR_REMINDER|CONSENT_UPDATE|GENERIC")
    String templateKey,
    
    @NotBlank
    String language,
    
    @NotBlank
    @Pattern(regexp = "EMAIL|SMS|WHATSAPP")
    String channel,
    
    @NotNull
    @Valid
    Audience audience,
    
    Map<String, String> variables
) {
    public record Audience(
        @NotBlank
        @Pattern(regexp = "BOARD|ALL_USERS|DATA_PRINCIPAL|USER_IDS")
        String type,
        
        String dataPrincipalId,
        
        @Size(min = 1)
        List<String> userIds
    ) {
    }
}
