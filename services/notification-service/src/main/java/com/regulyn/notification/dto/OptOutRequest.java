package com.regulyn.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OptOutRequest(
    @NotBlank
    String dataPrincipalId,
    
    @NotBlank
    @Pattern(regexp = "EMAIL|SMS|WHATSAPP")
    String channel,
    
    @NotBlank
    @Pattern(regexp = "LEGAL|MARKETING|SECURITY|OPERATIONS")
    String category,
    
    boolean optedOut
) {
}
