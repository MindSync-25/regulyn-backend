package com.regulyn.vendor.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class IngestVendorAccessEventsResponse {

    private int received;
    private int inserted;
    private int duplicates;
    private int failed;
    private List<IngestResult> results = new ArrayList<>();

    public int getReceived() {
        return received;
    }

    public void setReceived(int received) {
        this.received = received;
    }

    public int getInserted() {
        return inserted;
    }

    public void setInserted(int inserted) {
        this.inserted = inserted;
    }

    public int getDuplicates() {
        return duplicates;
    }

    public void setDuplicates(int duplicates) {
        this.duplicates = duplicates;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public List<IngestResult> getResults() {
        return results;
    }

    public void setResults(List<IngestResult> results) {
        this.results = results;
    }

    public void addResult(IngestResult result) {
        this.results.add(result);
    }

    public static class IngestResult {
        private String correlationId;
        private OffsetDateTime accessedAt;
        private String accessType;
        private String status;
        private String reason;

        public String getCorrelationId() {
            return correlationId;
        }

        public void setCorrelationId(String correlationId) {
            this.correlationId = correlationId;
        }

        public OffsetDateTime getAccessedAt() {
            return accessedAt;
        }

        public void setAccessedAt(OffsetDateTime accessedAt) {
            this.accessedAt = accessedAt;
        }

        public String getAccessType() {
            return accessType;
        }

        public void setAccessType(String accessType) {
            this.accessType = accessType;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
