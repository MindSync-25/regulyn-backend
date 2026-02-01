package com.regulyn.guardian.dto;

import java.time.Instant;
import java.util.UUID;

public record VerifyGuardianResponse(
    UUID guardianId,
    String status,
    Instant verifiedAt
) {}
