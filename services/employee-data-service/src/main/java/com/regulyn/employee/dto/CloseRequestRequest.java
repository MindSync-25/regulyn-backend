package com.regulyn.employee.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CloseRequestRequest {

    private String closureNotes;

    private List<UUID> includeEvidenceIds = new ArrayList<>();

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
