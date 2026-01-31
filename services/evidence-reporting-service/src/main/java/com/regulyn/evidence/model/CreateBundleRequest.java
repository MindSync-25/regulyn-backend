package com.regulyn.evidence.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record CreateBundleRequest(
    @NotBlank String bundleType,     // DSAR, DELETION, INCIDENT, NOMINEE, GUARDIAN, AUDIT_EXPORT
    @NotBlank String referenceType,  // DSAR, DELETION, INCIDENT, NOMINEE, GUARDIAN, PERIOD
    @NotBlank String referenceId,
    String title,
    String description,
    @NotEmpty List<String> evidenceIds,  // UUIDs as strings
    List<String> artifactIds,             // UUIDs as strings, optional
    Map<String, Object> metadata
) {}
