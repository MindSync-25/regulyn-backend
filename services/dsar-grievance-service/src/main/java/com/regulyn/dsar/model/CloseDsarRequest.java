package com.regulyn.dsar.model;

import java.util.List;
import java.util.UUID;

public class CloseDsarRequest {
    
    private String closureNotes;
    
    private List<UUID> includeEvidenceIds;

    // Getters and setters
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
