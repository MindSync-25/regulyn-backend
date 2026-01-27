package com.regulyn.evidence.model;

public class EvidenceResponse {
  private String evidenceId;
  private String status;

  public EvidenceResponse(String evidenceId, String status) {
    this.evidenceId = evidenceId;
    this.status = status;
  }

  public String getEvidenceId() { return evidenceId; }
  public void setEvidenceId(String evidenceId) { this.evidenceId = evidenceId; }
  
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
}
