package com.regulyn.retention.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CascadeExecuteResponse {
    private UUID deletionId;
    private UUID planId;
    private Integer planVersion;
    private String planHash;
    private List<CascadeSystemExecutionResponse> systems = new ArrayList<>();

    public UUID getDeletionId() { return deletionId; }
    public void setDeletionId(UUID deletionId) { this.deletionId = deletionId; }

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }

    public Integer getPlanVersion() { return planVersion; }
    public void setPlanVersion(Integer planVersion) { this.planVersion = planVersion; }

    public String getPlanHash() { return planHash; }
    public void setPlanHash(String planHash) { this.planHash = planHash; }

    public List<CascadeSystemExecutionResponse> getSystems() { return systems; }
    public void setSystems(List<CascadeSystemExecutionResponse> systems) { this.systems = systems; }
}
