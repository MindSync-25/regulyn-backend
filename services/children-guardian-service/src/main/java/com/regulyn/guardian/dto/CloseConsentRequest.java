package com.regulyn.guardian.dto;

import java.util.List;
import java.util.UUID;

public record CloseConsentRequest(
    String closureNotes,
    List<UUID> includeEvidenceIds
) {}
