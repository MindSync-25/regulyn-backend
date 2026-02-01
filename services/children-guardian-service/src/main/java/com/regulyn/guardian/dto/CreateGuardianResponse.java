package com.regulyn.guardian.dto;

import java.util.UUID;

public record CreateGuardianResponse(
    UUID guardianId,
    String status
) {}
