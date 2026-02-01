package com.regulyn.incident.dto;

import java.util.UUID;

public record CloseIncidentResponse(
    UUID incidentId,
    String status,
    UUID evidenceBundleId
) {}
