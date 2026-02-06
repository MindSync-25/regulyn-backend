package io.regulyn.connector.run;

import java.util.Map;

/**
 * Result of a connector run execution.
 */
public class ExecutionResult {

    private final Map<String, Object> newCursorJson;
    private final Map<String, Object> receiptMeta;

    public ExecutionResult(Map<String, Object> newCursorJson, Map<String, Object> receiptMeta) {
        this.newCursorJson = newCursorJson;
        this.receiptMeta = receiptMeta;
    }

    public Map<String, Object> getNewCursorJson() {
        return newCursorJson;
    }

    public Map<String, Object> getReceiptMeta() {
        return receiptMeta;
    }
}
