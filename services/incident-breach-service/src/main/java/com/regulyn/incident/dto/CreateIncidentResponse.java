package com.regulyn.incident.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateIncidentResponse(
    UUID incidentId,
    String status,
    Instant notifyDueAt
) {}
