package com.regulyn.retention.model;

public class CascadeSystemExecutionResponse {
    private String systemKey;
    private String subjectRef;
    private String status;
    private Integer attemptCount;
    private String externalJobRef;

    public String getSystemKey() { return systemKey; }
    public void setSystemKey(String systemKey) { this.systemKey = systemKey; }

    public String getSubjectRef() { return subjectRef; }
    public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }

    public String getExternalJobRef() { return externalJobRef; }
    public void setExternalJobRef(String externalJobRef) { this.externalJobRef = externalJobRef; }
}
