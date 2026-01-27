package com.regulyn.consent.model;

public class ConsentResponse {
  private String receiptId;
  private String hash;

  public ConsentResponse(String receiptId, String hash) {
    this.receiptId = receiptId;
    this.hash = hash;
  }

  public String getReceiptId() { return receiptId; }
  public void setReceiptId(String receiptId) { this.receiptId = receiptId; }
  
  public String getHash() { return hash; }
  public void setHash(String hash) { this.hash = hash; }
}
