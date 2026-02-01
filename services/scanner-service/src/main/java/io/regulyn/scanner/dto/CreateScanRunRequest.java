package io.regulyn.scanner.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public class CreateScanRunRequest {

    @NotNull(message = "sourceId is required")
    private UUID sourceId;

    @NotNull(message = "scanMode is required")
    private ScanMode scanMode;

    private Instant since;

    private String requestRef;

    // Enums
    public enum ScanMode {
        INVENTORY,
        RETENTION_CANDIDATES,
        BOTH
    }

    // Getters and Setters
    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public ScanMode getScanMode() {
        return scanMode;
    }

    public void setScanMode(ScanMode scanMode) {
        this.scanMode = scanMode;
    }

    public Instant getSince() {
        return since;
    }

    public void setSince(Instant since) {
        this.since = since;
    }

    public String getRequestRef() {
        return requestRef;
    }

    public void setRequestRef(String requestRef) {
        this.requestRef = requestRef;
    }
}
