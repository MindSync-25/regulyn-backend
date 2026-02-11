package com.regulyn.guardian.dto;

import jakarta.validation.constraints.NotBlank;

public record AdultConsentLinkRequest(
        @NotBlank(message = "consentReceiptRef is required")
        String consentReceiptRef,

        @NotBlank(message = "consentReceiptSha256 is required")
        String consentReceiptSha256
) {}
