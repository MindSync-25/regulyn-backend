package com.regulyn.guardian.dto;

import java.time.LocalDate;
import java.util.Map;

public record CreateExportRequest(
    LocalDate periodFrom,
    LocalDate periodTo,
    Map<String, Object> filters
) {}
