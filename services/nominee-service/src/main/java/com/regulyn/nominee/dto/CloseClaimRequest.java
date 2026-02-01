package com.regulyn.nominee.dto;

import java.util.List;
import java.util.UUID;

public class CloseClaimRequest {

    private String closureNotes;

    private List<UUID> includeEvidenceIds;

    // Getters and Setters
    public String getClosureNotes() {
        return closureNotes;
    }

    public void setClosureNotes(String closureNotes) {
        this.closureNotes = closureNotes;
    }

    public List<UUID> getIncludeEvidenceIds() {
        return includeEvidenceIds;
    }

    public void setIncludeEvidenceIds(List<UUID> includeEvidenceIds) {
        this.includeEvidenceIds = includeEvidenceIds;
    }
}
