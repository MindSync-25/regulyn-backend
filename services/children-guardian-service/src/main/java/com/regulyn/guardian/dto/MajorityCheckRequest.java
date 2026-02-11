package com.regulyn.guardian.dto;

import java.time.LocalDate;

public record MajorityCheckRequest(
        LocalDate evaluationDate
) {}
