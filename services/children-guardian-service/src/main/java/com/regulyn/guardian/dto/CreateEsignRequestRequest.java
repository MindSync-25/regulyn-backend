package com.regulyn.guardian.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateEsignRequestRequest(
        @NotBlank String provider,
        @NotBlank String docType,
        @NotNull Integer docVersion,
        String returnUrl
) {
}
