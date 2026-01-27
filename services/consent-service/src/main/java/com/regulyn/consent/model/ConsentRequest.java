package com.regulyn.consent.model;

public class ConsentRequest {
  private String userId;
  private String purpose;
  private String language;
  private String noticeText;
  private String source;

  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  
  public String getPurpose() { return purpose; }
  public void setPurpose(String purpose) { this.purpose = purpose; }
  
  public String getLanguage() { return language; }
  public void setLanguage(String language) { this.language = language; }
  
  public String getNoticeText() { return noticeText; }
  public void setNoticeText(String noticeText) { this.noticeText = noticeText; }
  
  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }
}
