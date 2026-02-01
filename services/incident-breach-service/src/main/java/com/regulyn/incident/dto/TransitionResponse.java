package com.regulyn.incident.dto;

import java.util.UUID;

public record TransitionResponse(
    UUID incidentId,
    String status
) {}
