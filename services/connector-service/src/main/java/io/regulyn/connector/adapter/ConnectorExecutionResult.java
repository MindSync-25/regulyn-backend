package io.regulyn.connector.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConnectorExecutionResult {

    private String status; // SUCCEEDED, FAILED
    private Map<String, Object> responseJson;
    private List<String> artifactRefs = new ArrayList<>();
    private String resultHash;
    private String error;

    // Getters and Setters
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getResponseJson() {
        return responseJson;
    }

    public void setResponseJson(Map<String, Object> responseJson) {
        this.responseJson = responseJson;
    }

    public List<String> getArtifactRefs() {
        return artifactRefs;
    }

    public void setArtifactRefs(List<String> artifactRefs) {
        this.artifactRefs = artifactRefs;
    }

    public String getResultHash() {
        return resultHash;
    }

    public void setResultHash(String resultHash) {
        this.resultHash = resultHash;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
