package com.regulyn.incident.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IncidentDetailsResponse(
    UUID incidentId,
    UUID tenantId,
    String status,
    String severity,
    Instant openedAt,
    Instant notifyDueAt,
    Instant updatedAt,
    String summary,
    UUID approvedBy,
    Instant approvedAt,
    Instant closedAt,
    UUID evidenceBundleId,
    String closureNotes,
    Boolean notifyOverdue,
    Map<String, Object> metadata
) {}
