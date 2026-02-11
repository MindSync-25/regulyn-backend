package com.regulyn.guardian.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AgeEvaluationRequest(
        @Size(min = 2, max = 2, message = "countryCode must be 2 letters")
        String countryCode,
        String stateCode,
        LocalDate evaluationDate
) {}
