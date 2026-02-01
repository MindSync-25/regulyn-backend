package com.regulyn.incident.dto;

import java.util.List;
import java.util.UUID;

public record CloseIncidentRequest(
    String closureNotes,
    List<UUID> includeEvidenceIds
) {
    public CloseIncidentRequest {
        if (includeEvidenceIds == null) {
            includeEvidenceIds = List.of();
        }
    }
}
