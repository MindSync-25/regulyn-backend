package com.regulyn.guardian.dto;

import jakarta.validation.constraints.NotBlank;

public record EvidenceExportRequest(
        @NotBlank(message = "exportScope is required")
        String exportScope
) {}
