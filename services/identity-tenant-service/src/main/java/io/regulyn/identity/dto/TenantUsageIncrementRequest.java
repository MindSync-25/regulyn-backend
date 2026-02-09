package io.regulyn.identity.dto;

public class TenantUsageIncrementRequest {
    private String idempotencyKey;
    private String requestHash;
    private Integer dsarIncrement;
    private Integer exportIncrement;

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public Integer getDsarIncrement() {
        return dsarIncrement;
    }

    public void setDsarIncrement(Integer dsarIncrement) {
        this.dsarIncrement = dsarIncrement;
    }

    public Integer getExportIncrement() {
        return exportIncrement;
    }

    public void setExportIncrement(Integer exportIncrement) {
        this.exportIncrement = exportIncrement;
    }
}
