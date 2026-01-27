package io.regulyn.identity.dto;

public class ValidateApiKeyRequest {
    private String apiKey;

    public ValidateApiKeyRequest() {}

    public ValidateApiKeyRequest(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
