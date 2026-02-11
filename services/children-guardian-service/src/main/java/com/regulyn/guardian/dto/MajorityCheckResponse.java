package com.regulyn.guardian.dto;

import java.time.LocalDate;
import java.util.UUID;

public record MajorityCheckResponse(
        UUID childId,
        LocalDate majorityDate,
        boolean reached,
        String transitionStatus,
        String resolvedFrom,
        String error
) {}
